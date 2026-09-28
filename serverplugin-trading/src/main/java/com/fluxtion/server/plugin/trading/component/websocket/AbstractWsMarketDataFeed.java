package com.fluxtion.server.plugin.trading.component.websocket;

import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.server.plugin.trading.component.marketdatafeed.AbstractMarketDataFeed;
import com.fluxtion.server.plugin.trading.service.marketdata.MarketConnected;
import com.fluxtion.server.plugin.trading.service.marketdata.MarketDataBook;
import com.fluxtion.server.plugin.trading.service.marketdata.MarketDisconnected;
import com.fluxtion.server.plugin.trading.service.marketdata.MarketFeedEvent;
import com.fluxtion.server.service.admin.AdminCommandRegistry;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Live WebSocket market-data feed base, built on the JDK {@link java.net.http.WebSocket} client
 * (no WebSocket library). It is NOT an Agrona agent: like the FIX feeds it publishes straight from
 * the WebSocket callback, and the inherited {@code targetQueue} ({@code EventToQueuePublisher}) is the
 * thread-safe hand-off into the Fluxtion processor. The {@link HttpClient} runs on a dedicated
 * single-thread executor we own, so delivery is single-threaded, ordered and controllable.
 *
 * <p>Self-contained like the Talos non-agent feed: it connects from the framework {@code start()}
 * lifecycle and reconnects on its own scheduled executor (no dependency on the group SchedulerService).
 * Control rides {@link AdminCommandRegistry}. A concrete exchange feed supplies {@link #onMessage} and,
 * for venues with a subscribe handshake, {@link #subscribeFrame}/{@link #unsubscribeFrame}.</p>
 *
 * <p><b>Venue hooks.</b> Everything else is a protected hook with a sensible default:</p>
 * <ul>
 *   <li>{@link #headers} / {@link #configureWebSocket(WebSocket.Builder)} — handshake headers (API keys)
 *       and any other {@link WebSocket.Builder} tweak.</li>
 *   <li>{@link #subscribeFrame} / {@link #unsubscribeFrame} return {@code null} by default: a push-only
 *       venue (streams everything, no subscribe handshake) overrides neither and a subscription is then
 *       purely a client-side filter via {@link #isSubscribed(String)}.</li>
 *   <li>{@link #isSubscribed(String)} — exact match by default; override for prefix/wildcard rules.</li>
 *   <li>{@link #onConnected()} / {@link #onDisconnected()} — lifecycle notifications, e.g. to flush
 *       venue-health state when the socket drops. Fired for every loss of the socket: an unexpected drop
 *       (WS thread), the watchdog, an admin {@code disconnect}/{@code reconnect} (admin thread) and
 *       {@code stop()} (framework thread). {@link MarketDisconnected} is published in the same cases.</li>
 *   <li>{@link #statusExtra()} — extra lines appended to the {@code <feed>.status} admin output.</li>
 *   <li>{@link #staleConnectionMillis} — watchdog: if no frame of any kind (text, binary, ping or pong)
 *       arrives for this long the socket is aborted and the normal reconnect path runs (a half-open TCP
 *       link never fires onClose on its own). Venue pings therefore keep a quiet link alive.</li>
 * </ul>
 *
 * <p><b>Admin semantics.</b> {@code <feed>.disconnect} closes the socket and suspends automatic reconnect
 * until {@code <feed>.connect} (or a framework {@code start()}); {@code <feed>.reconnect} closes and
 * immediately reopens. Callbacks from a socket that is no longer current (e.g. the close acknowledgement
 * of the old socket arriving after the new one opened) are ignored, so a reconnect never disturbs the
 * live connection.</p>
 */
@Log4j2
public abstract class AbstractWsMarketDataFeed extends AbstractMarketDataFeed {

    @Getter @Setter protected String url;
    @Getter @Setter protected int reconnectMillis = 5000;
    @Getter @Setter protected int connectTimeoutSeconds = 10;
    /** Handshake headers (e.g. an API-key header). Inject secrets from config / a secret store, never source. */
    @Getter @Setter protected Map<String, String> headers = new LinkedHashMap<>();
    /** Abort + reconnect when no frame has arrived for this long; {@code <= 0} disables the watchdog. */
    @Getter @Setter protected long staleConnectionMillis = 0L;

    private ScheduledExecutorService wsExecutor;
    private HttpClient httpClient;
    private volatile WebSocket webSocket;
    private volatile boolean connected = false;
    private volatile boolean everConnected = false;
    private volatile boolean shuttingDown = false;
    /** False after an admin disconnect: the feed stays down until an explicit connect. */
    private volatile boolean reconnectEnabled = true;
    /** A buildAsync is in flight (not yet open): guards against opening a second socket. */
    private volatile boolean connectPending = false;

    private final StringBuilder textBuffer = new StringBuilder();
    private final AtomicLong messagesReceived = new AtomicLong();
    private final AtomicLong reconnects = new AtomicLong();
    private volatile long lastMessageTime = 0L;
    /** The pending watchdog check for the current socket; cancelled on disconnect so chains never overlap. */
    private volatile ScheduledFuture<?> staleCheckFuture;

    // ==================== exchange-specific hooks ====================

    /**
     * Build the venue subscribe message for a symbol (sent as a WS text frame). Return {@code null}
     * (the default) for a push-only venue: nothing is sent and the subscription is a client-side filter.
     */
    protected String subscribeFrame(String feedName, String venueName, String symbol) {
        return null;
    }

    /** Build the venue unsubscribe message for a symbol (sent as a WS text frame); {@code null} sends nothing. */
    protected String unsubscribeFrame(String feedName, String venueName, String symbol) {
        return null;
    }

    /** Parse one inbound venue message and {@link #publish} any resulting market events. */
    protected abstract void onMessage(String rawJson);

    /** Customise the handshake; the default applies {@link #headers}. Called on every (re)connect. */
    protected void configureWebSocket(WebSocket.Builder builder) {
        if (headers != null) {
            headers.forEach(builder::header);
        }
    }

    /** Whether an inbound symbol is covered by the current {@link #subscriptions}. Exact match by default. */
    protected boolean isSubscribed(String symbol) {
        return symbol != null && subscriptions.contains(symbol);
    }

    /** Called on the WS thread after {@link MarketConnected} is published. */
    protected void onConnected() {
    }

    /**
     * Called whenever the socket is lost, after {@link MarketDisconnected} is published and before any
     * reconnect: on the WS thread for a drop or watchdog abort, on the caller's thread for an admin
     * disconnect/reconnect or {@code stop()}.
     */
    protected void onDisconnected() {
    }

    /** Extra lines for the {@code <feed>.status} admin command; {@code null}/empty adds nothing. */
    protected String statusExtra() {
        return null;
    }

    // ==================== framework wiring ====================

    @Override
    public void start() {
        super.start();
        shuttingDown = false;
        reconnectEnabled = true;
        connect();
    }

    @ServiceRegistered
    public void adminClient(AdminCommandRegistry registry) {
        registry.registerCommand(getFeedName() + ".connect", (a, out, err) -> {
            reconnectEnabled = true;
            connect();
            out.accept("connect requested to " + url);
        });
        registry.registerCommand(getFeedName() + ".disconnect", (a, out, err) -> {
            reconnectEnabled = false; // stay down until an explicit connect
            closeWebSocket("admin");
            out.accept("disconnected; automatic reconnect suspended until " + getFeedName() + ".connect");
        });
        registry.registerCommand(getFeedName() + ".reconnect", (a, out, err) -> {
            reconnectEnabled = true;
            closeWebSocket("admin-reconnect");
            connect();
            out.accept("reconnect requested");
        });
        registry.registerCommand(getFeedName() + ".subscribe", this::subscribeCmd);
        registry.registerCommand(getFeedName() + ".unsubscribe", (a, out, err) -> {
            if (a.size() != 2) {
                err.accept("unsubscribe requires 1 argument [symbol]");
                return;
            }
            unsubscribe(getFeedName(), getFeedName(), a.get(1));
            out.accept("unsubscribed " + a.get(1));
        });
        registry.registerCommand(getFeedName() + ".subscriptions", (a, out, err) ->
                out.accept(getFeedName() + " subscriptions: " + subscriptions));
        registry.registerCommand(getFeedName() + ".status", this::statusCmd);
    }

    @Override
    protected void subscribeToSymbol(String feedName, String venueName, String symbol) {
        subscriptions.add(symbol);
        if (connected && webSocket != null) {
            sendText(subscribeFrame(feedName, venueName, symbol));
        } else {
            log.info("{} not connected; caching subscription {}", getFeedName(), symbol);
        }
    }

    @Override
    public void unsubscribe(String feedName, String venueName, String symbol) {
        subscriptions.remove(symbol);
        if (connected && webSocket != null) {
            sendText(unsubscribeFrame(feedName, venueName, symbol));
        }
        // Short-term end-of-subscription signal: the venue stops streaming this symbol, so the last
        // thing a listener would ever see is a stale book. Publish an explicit EMPTY book (all prices
        // /qty/orders zero) so downstream sees the subscription end and its series visibly flatlines to
        // 0 rather than freezing at the last tick. (A dedicated MarketDataListener end-of-subscription
        // callback is deferred — see websocket-market-data-feed.md.)
        MarketDataBook empty = new MarketDataBook(getFeedName(), venueName, symbol, 0L, 0d, 0d, 0d, 0d);
        empty.setBidOrderCount(0);
        empty.setAskOrderCount(0);
        publish(empty);
        log.info("{} unsubscribed {} (published empty book)", getFeedName(), symbol);
    }

    @Override
    public void stop() {
        super.stop();
        shuttingDown = true;
        closeWebSocket("stop");
    }

    @Override
    public void tearDown() {
        super.tearDown();
        shuttingDown = true;
        closeWebSocket("tearDown");
        if (wsExecutor != null) {
            wsExecutor.shutdownNow();
        }
    }

    // ==================== connection lifecycle ====================

    synchronized void connect() {
        if (shuttingDown || connected || connectPending || url == null) {
            return;
        }
        if (wsExecutor == null) {
            wsExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, getFeedName() + "-ws");
                t.setDaemon(true);
                return t;
            });
            httpClient = HttpClient.newBuilder().executor(wsExecutor).build();
        }
        try {
            log.info("{} connecting to {}", getFeedName(), url);
            WebSocket.Builder builder = httpClient.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds));
            configureWebSocket(builder);
            // The socket reference is captured in FeedListener.onOpen, which the JDK invokes BEFORE this
            // future completes; assigning it here would leave it null during the on-open subscribe replay.
            connectPending = true;
            builder.buildAsync(URI.create(url), new FeedListener())
                    .whenComplete((ws, ex) -> {
                        connectPending = false;
                        if (ex != null) {
                            log.warn("{} connect failed: {}", getFeedName(), ex.toString());
                            scheduleReconnect();
                        }
                    });
        } catch (Exception e) {
            connectPending = false;
            log.error("{} connect error", getFeedName(), e);
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (shuttingDown || !reconnectEnabled || wsExecutor == null) {
            return;
        }
        // Until the first successful connect, retry fast (the venue socket may just not be up yet at
        // boot — the startup race); afterwards use the configured steady-state reconnect interval.
        long delay = everConnected ? reconnectMillis : Math.min(reconnectMillis, 500);
        wsExecutor.schedule(() -> {
            if (!connected && !connectPending && !shuttingDown && reconnectEnabled) {
                reconnects.incrementAndGet();
                connect();
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    /** Deliberate close (admin or stop): the graph is told the feed is down, same as an unexpected drop. */
    private synchronized void closeWebSocket(String reason) {
        boolean wasConnected = connected;
        connected = false;
        cancelStaleCheck();
        WebSocket ws = webSocket;
        webSocket = null; // callbacks from this socket are now stale and ignored by FeedListener
        if (ws != null) {
            log.info("{} closing socket ({})", getFeedName(), reason);
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, reason);
            } catch (Exception ignore) {
                ws.abort();
            }
        }
        if (wasConnected) {
            notifyDisconnected();
        }
    }

    /** Send a text frame; a {@code null}/empty frame or a missing socket is a no-op. */
    protected void sendText(String text) {
        WebSocket ws = webSocket;
        if (ws != null && text != null && !text.isEmpty()) {
            ws.sendText(text, true);
        }
    }

    // ==================== stale-connection watchdog ====================

    private void scheduleStaleCheck(long delayMillis) {
        if (staleConnectionMillis <= 0 || wsExecutor == null || shuttingDown) {
            return;
        }
        staleCheckFuture = wsExecutor.schedule(this::staleCheck, delayMillis, TimeUnit.MILLISECONDS);
    }

    private void cancelStaleCheck() {
        ScheduledFuture<?> pending = staleCheckFuture;
        staleCheckFuture = null;
        if (pending != null) {
            pending.cancel(false);
        }
    }

    private void staleCheck() {
        if (!connected || shuttingDown) {
            return;
        }
        long age = System.currentTimeMillis() - lastMessageTime;
        if (age >= staleConnectionMillis) {
            log.warn("{} no frame for {}ms (limit {}ms) — aborting socket", getFeedName(), age, staleConnectionMillis);
            WebSocket ws = webSocket;
            if (ws != null) {
                ws.abort();
            }
            // abort() fires neither onClose nor onError, so run the disconnect path explicitly
            markDisconnected();
        } else {
            scheduleStaleCheck(staleConnectionMillis - age);
        }
    }

    /** Any inbound frame (text, binary, ping, pong) proves the link is alive. */
    private void touchLastMessageTime() {
        lastMessageTime = System.currentTimeMillis();
    }

    // ==================== the WebSocket callback (single-thread executor) ====================

    private final class FeedListener implements WebSocket.Listener {
        @Override
        public void onOpen(WebSocket ws) {
            // capture the socket FIRST: sendText below reads the field, and buildAsync's future has
            // not completed yet when onOpen runs
            webSocket = ws;
            connectPending = false;
            connected = true;
            everConnected = true;
            touchLastMessageTime();
            log.info("{} connected to {}", getFeedName(), url);
            publish(new MarketConnected(getFeedName()));
            onConnected();
            resendSubscriptions();
            scheduleStaleCheck(staleConnectionMillis);
            // Belt-and-suspenders: re-send shortly after connect. Graph nodes may subscribe in
            // REACTION to the MarketConnected we just published (so `subscriptions` may still be
            // empty here); a short delayed re-send guarantees a boot-time subscription reaches the venue.
            if (wsExecutor != null) {
                wsExecutor.schedule(AbstractWsMarketDataFeed.this::resendSubscriptions, 500, TimeUnit.MILLISECONDS);
            }
            ws.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            if (isStale(ws)) {
                return null;
            }
            textBuffer.append(data);
            if (last) {
                String message = textBuffer.toString();
                textBuffer.setLength(0);
                messagesReceived.incrementAndGet();
                touchLastMessageTime();
                try {
                    onMessage(message);
                } catch (Exception e) {
                    log.warn("{} failed to handle message: {}", getFeedName(), e.toString());
                }
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket ws, ByteBuffer data, boolean last) {
            touchLastMessageTime();
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onPing(WebSocket ws, ByteBuffer message) {
            touchLastMessageTime(); // the JDK client answers the ping with a pong itself
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onPong(WebSocket ws, ByteBuffer message) {
            touchLastMessageTime();
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            if (isStale(ws)) {
                log.debug("{} stale socket closed {} {}", getFeedName(), statusCode, reason);
                return null;
            }
            log.info("{} closed {} {}", getFeedName(), statusCode, reason);
            markDisconnected();
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            if (isStale(ws)) {
                log.debug("{} stale socket error: {}", getFeedName(), error.toString());
                return;
            }
            log.warn("{} websocket error: {}", getFeedName(), error.toString());
            markDisconnected();
        }

        /**
         * A callback from a socket we have already replaced or deliberately closed. Its close
         * acknowledgement/EOF must not tear down the live connection or trigger a reconnect.
         */
        private boolean isStale(WebSocket ws) {
            return ws != webSocket;
        }
    }

    private void resendSubscriptions() {
        for (String symbol : subscriptions) {
            sendText(subscribeFrame(getFeedName(), getFeedName(), symbol));
        }
    }

    /** Unexpected loss of the current socket (drop, error, watchdog): notify and schedule a reconnect. */
    private void markDisconnected() {
        boolean wasConnected = connected;
        connected = false;
        cancelStaleCheck();
        webSocket = null;
        if (wasConnected) {
            notifyDisconnected();
        }
        scheduleReconnect();
    }

    private void notifyDisconnected() {
        publish(new MarketDisconnected(getFeedName()));
        onDisconnected();
    }

    /** for subclasses that need to emit directly. */
    protected void publishEvent(MarketFeedEvent event) {
        if (event != null) {
            publish(event);
        }
    }

    // ==================== admin ====================

    private void subscribeCmd(List<String> args, Consumer<String> out, Consumer<String> err) {
        if (args.size() != 2) {
            err.accept("subscribe requires 1 argument [symbol]");
            return;
        }
        String symbol = args.get(1);
        subscribeToSymbol(getFeedName(), getFeedName(), symbol);
        out.accept("subscribed " + symbol);
    }

    private void statusCmd(List<String> args, Consumer<String> out, Consumer<String> err) {
        String status = String.format(
                "%s%n  url: %s%n  connected: %s%n  reconnectEnabled: %s%n  subscriptions: %s%n  messagesReceived: %d%n  reconnects: %d%n  lastMessageAgeMs: %s",
                getFeedName(), url, connected, reconnectEnabled, subscriptions, messagesReceived.get(), reconnects.get(),
                lastMessageTime == 0 ? "n/a" : (System.currentTimeMillis() - lastMessageTime));
        String extra = statusExtra();
        out.accept(extra == null || extra.isEmpty() ? status : status + System.lineSeparator() + extra);
    }

    // ==================== diagnostics (for subclasses and tests) ====================

    public boolean isConnected() {
        return connected;
    }

    public long messagesReceived() {
        return messagesReceived.get();
    }

    public long reconnects() {
        return reconnects.get();
    }

    public long lastMessageTime() {
        return lastMessageTime;
    }

    public boolean isReconnectEnabled() {
        return reconnectEnabled;
    }
}
