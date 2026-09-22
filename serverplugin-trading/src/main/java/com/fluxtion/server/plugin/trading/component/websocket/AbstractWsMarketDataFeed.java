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
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
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
 * Control rides {@link AdminCommandRegistry}. A concrete exchange feed supplies only
 * {@link #subscribeFrame} and {@link #onMessage}.</p>
 */
@Log4j2
public abstract class AbstractWsMarketDataFeed extends AbstractMarketDataFeed {

    @Getter @Setter protected String url;
    @Getter @Setter protected int reconnectMillis = 5000;

    private ScheduledExecutorService wsExecutor;
    private HttpClient httpClient;
    private volatile WebSocket webSocket;
    private volatile boolean connected = false;
    private volatile boolean everConnected = false;
    private volatile boolean shuttingDown = false;

    private final StringBuilder textBuffer = new StringBuilder();
    private final AtomicLong messagesReceived = new AtomicLong();
    private final AtomicLong reconnects = new AtomicLong();
    private volatile long lastMessageTime = 0L;

    // ==================== exchange-specific hooks ====================

    /** Build the venue subscribe message for a symbol (sent as a WS text frame). */
    protected abstract String subscribeFrame(String feedName, String venueName, String symbol);

    /** Build the venue unsubscribe message for a symbol (sent as a WS text frame). */
    protected abstract String unsubscribeFrame(String feedName, String venueName, String symbol);

    /** Parse one inbound venue message and {@link #publish} any resulting market events. */
    protected abstract void onMessage(String rawJson);

    // ==================== framework wiring ====================

    @Override
    public void start() {
        super.start();
        shuttingDown = false;
        connect();
    }

    @ServiceRegistered
    public void adminClient(AdminCommandRegistry registry) {
        registry.registerCommand(getFeedName() + ".connect", (a, out, err) -> {
            connect();
            out.accept("connect requested to " + url);
        });
        registry.registerCommand(getFeedName() + ".disconnect", (a, out, err) -> {
            closeWebSocket("admin");
            out.accept("disconnected");
        });
        registry.registerCommand(getFeedName() + ".reconnect", (a, out, err) -> {
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
        if (shuttingDown || connected || url == null) {
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
            httpClient.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .buildAsync(URI.create(url), new FeedListener())
                    .whenComplete((ws, ex) -> {
                        if (ex != null) {
                            log.warn("{} connect failed: {}", getFeedName(), ex.toString());
                            scheduleReconnect();
                        } else {
                            webSocket = ws;
                        }
                    });
        } catch (Exception e) {
            log.error("{} connect error", getFeedName(), e);
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (shuttingDown || wsExecutor == null) {
            return;
        }
        // Until the first successful connect, retry fast (the venue socket may just not be up yet at
        // boot — the startup race); afterwards use the configured steady-state reconnect interval.
        long delay = everConnected ? reconnectMillis : Math.min(reconnectMillis, 500);
        wsExecutor.schedule(() -> {
            if (!connected && !shuttingDown) {
                reconnects.incrementAndGet();
                connect();
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private synchronized void closeWebSocket(String reason) {
        connected = false;
        WebSocket ws = webSocket;
        webSocket = null;
        if (ws != null) {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, reason);
            } catch (Exception ignore) {
                ws.abort();
            }
        }
    }

    protected void sendText(String text) {
        WebSocket ws = webSocket;
        if (ws != null) {
            ws.sendText(text, true);
        }
    }

    // ==================== the WebSocket callback (single-thread executor) ====================

    private final class FeedListener implements WebSocket.Listener {
        @Override
        public void onOpen(WebSocket ws) {
            connected = true;
            everConnected = true;
            log.info("{} connected to {}", getFeedName(), url);
            publish(new MarketConnected(getFeedName()));
            resendSubscriptions();
            // Belt-and-suspenders: re-send shortly after connect. Graph nodes may subscribe in
            // REACTION to the MarketConnected we just published (so `subscriptions` is still empty
            // here), and the venue's per-client reader may not be draining yet at the instant of
            // onOpen. A short delayed re-send guarantees a boot-time subscription reaches the venue.
            if (wsExecutor != null) {
                wsExecutor.schedule(AbstractWsMarketDataFeed.this::resendSubscriptions, 500, TimeUnit.MILLISECONDS);
            }
            ws.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            textBuffer.append(data);
            if (last) {
                String message = textBuffer.toString();
                textBuffer.setLength(0);
                messagesReceived.incrementAndGet();
                lastMessageTime = System.currentTimeMillis();
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
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            log.info("{} closed {} {}", getFeedName(), statusCode, reason);
            markDisconnected();
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            log.warn("{} websocket error: {}", getFeedName(), error.toString());
            markDisconnected();
        }
    }

    private void resendSubscriptions() {
        for (String symbol : subscriptions) {
            sendText(subscribeFrame(getFeedName(), getFeedName(), symbol));
        }
    }

    private void markDisconnected() {
        boolean wasConnected = connected;
        connected = false;
        webSocket = null;
        if (wasConnected) {
            publish(new MarketDisconnected(getFeedName()));
        }
        scheduleReconnect();
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
        out.accept(String.format(
                "%s%n  url: %s%n  connected: %s%n  subscriptions: %s%n  messagesReceived: %d%n  reconnects: %d%n  lastMessageAgeMs: %s",
                getFeedName(), url, connected, subscriptions, messagesReceived.get(), reconnects.get(),
                lastMessageTime == 0 ? "n/a" : (System.currentTimeMillis() - lastMessageTime)));
    }

    // exposed for tests
    boolean isConnected() {
        return connected;
    }

    long messagesReceived() {
        return messagesReceived.get();
    }
}
