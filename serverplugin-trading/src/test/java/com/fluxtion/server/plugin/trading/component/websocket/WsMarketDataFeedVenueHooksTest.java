package com.fluxtion.server.plugin.trading.component.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxtion.server.plugin.trading.component.mockvenue.mktdata.MarketDataBookConfig;
import com.fluxtion.server.plugin.trading.component.mockvenue.wsmktdata.MockWsMarketDataPublisher;
import com.fluxtion.server.plugin.trading.service.marketdata.MarketConnected;
import com.fluxtion.server.plugin.trading.service.marketdata.MarketDataBook;
import com.fluxtion.server.plugin.trading.service.marketdata.MarketDisconnected;
import com.fluxtion.server.plugin.trading.service.marketdata.MarketFeedEvent;
import com.fluxtion.server.service.admin.AdminCommandRegistry;
import com.fluxtion.server.service.admin.AdminCommandRequest;
import com.fluxtion.server.service.admin.AdminFunction;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the venue hooks on {@link AbstractWsMarketDataFeed} with a push-only feed: no subscribe
 * frames, prefix subscriptions, handshake headers, lifecycle callbacks, status extension and the
 * stale-connection watchdog.
 */
class WsMarketDataFeedVenueHooksTest {

    /** A push-only venue feed: overrides neither subscribe frame, filters inbound books client-side. */
    static final class PushOnlyFeed extends AbstractWsMarketDataFeed {
        final CopyOnWriteArrayList<MarketFeedEvent> events = new CopyOnWriteArrayList<>();
        final AtomicInteger connectedCalls = new AtomicInteger();
        final AtomicInteger disconnectedCalls = new AtomicInteger();
        final AtomicInteger builderCalls = new AtomicInteger();
        final AtomicInteger suppressed = new AtomicInteger();
        private final ObjectMapper mapper = new ObjectMapper();

        @Override
        protected void publish(MarketFeedEvent event) {
            events.add(event); // no EventFlowManager wired in this unit test
        }

        @Override
        protected void onMessage(String rawJson) {
            try {
                MarketFeedEvent event = mapper.readValue(rawJson, WsMarketDataMessage.class).toEvent();
                if (event instanceof MarketDataBook book && !isSubscribed(book.getSymbol())) {
                    suppressed.incrementAndGet();
                    return;
                }
                publish(event);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        /** {@code PREFIX*} matches by prefix so a rolling contract needs no re-subscribe. */
        @Override
        protected boolean isSubscribed(String symbol) {
            if (super.isSubscribed(symbol)) {
                return true;
            }
            for (String sub : subscriptions) {
                if (sub.endsWith("*") && symbol.startsWith(sub.substring(0, sub.length() - 1))) {
                    return true;
                }
            }
            return false;
        }

        @Override
        protected void configureWebSocket(WebSocket.Builder builder) {
            super.configureWebSocket(builder);
            builderCalls.incrementAndGet();
        }

        @Override
        protected void onConnected() {
            connectedCalls.incrementAndGet();
        }

        @Override
        protected void onDisconnected() {
            disconnectedCalls.incrementAndGet();
        }

        @Override
        protected String statusExtra() {
            return "  suppressed: " + suppressed.get();
        }
    }

    @Test
    void pushOnlyFeedFiltersByPrefixAndReportsLifecycle() throws Exception {
        int port = freePort();
        MockWsMarketDataPublisher venue = new MockWsMarketDataPublisher();
        venue.setName("hooksVenue");
        venue.setPort(port);
        venue.setMarketDataBookConfigs(List.of(config("ABC-1"), config("XYZ-1")));
        venue.ensureStarted();

        PushOnlyFeed feed = new PushOnlyFeed();
        feed.setFeedName("pushOnlyFeed");
        feed.setUrl("ws://localhost:" + port + "/");
        feed.setHeaders(Map.of("X-Test-Header", "test-value"));
        feed.subscribeToSymbol("pushOnlyFeed", "hooksVenue", "ABC*");
        feed.connect();

        waitUntil(feed::isConnected, 3000);
        assertTrue(feed.isConnected(), "feed should connect");
        assertEquals(1, feed.connectedCalls.get(), "onConnected hook should fire once");
        assertEquals(1, feed.builderCalls.get(), "configureWebSocket should run on connect");
        assertEquals("test-value", venue.lastHandshakeHeaders().get("x-test-header"),
                "configured handshake header should reach the venue");

        waitUntil(() -> venue.clientCount() > 0, 2000);
        for (int i = 0; i < 50 && feed.suppressed.get() == 0; i++) {
            venue.timerTriggered();
            Thread.sleep(40);
        }

        List<String> symbols = feed.events.stream()
                .filter(e -> e instanceof MarketDataBook).map(e -> ((MarketDataBook) e).getSymbol())
                .distinct().toList();
        assertEquals(List.of("ABC-1"), symbols, "only the prefix-matched symbol should be published");
        assertTrue(feed.suppressed.get() > 0, "non-matching symbol should be filtered client-side");
        assertTrue(venue.subscribedSymbols().isEmpty(), "push-only feed must send no subscribe frame");

        // the registered status command must append statusExtra() to the base status block
        Map<String, AdminFunction<String, String>> commands = registerCommands(feed);
        List<String> out = new ArrayList<>();
        commands.get("pushOnlyFeed.status").processAdminCommand(List.of("pushOnlyFeed.status"), out::add, err -> {
            throw new AssertionError("status command reported an error: " + err);
        });
        assertEquals(1, out.size(), "status command should write one block");
        assertTrue(out.get(0).contains("connected: true"), "status should carry the base fields: " + out.get(0));
        assertTrue(out.get(0).contains("suppressed: " + feed.suppressed.get()),
                "status should append statusExtra(): " + out.get(0));

        // drop the venue: the feed must notify the hook and emit MarketDisconnected
        venue.closeClients();
        waitUntil(() -> feed.disconnectedCalls.get() > 0, 3000);
        assertEquals(1, feed.disconnectedCalls.get(), "onDisconnected hook should fire once");
        assertTrue(feed.events.stream().anyMatch(e -> e instanceof MarketDisconnected));
        assertFalse(feed.isConnected());

        feed.tearDown();
    }

    @Test
    void staleConnectionWatchdogAbortsAndReconnects() throws Exception {
        int port = freePort();
        MockWsMarketDataPublisher venue = new MockWsMarketDataPublisher();
        venue.setName("staleVenue");
        venue.setPort(port);
        venue.setMarketDataBookConfigs(List.of(config("ABC-1")));
        venue.ensureStarted(); // never triggered: no frames ever flow

        PushOnlyFeed feed = new PushOnlyFeed();
        feed.setFeedName("staleFeed");
        feed.setUrl("ws://localhost:" + port + "/");
        feed.setStaleConnectionMillis(200);
        feed.setReconnectMillis(100);
        feed.connect();

        waitUntil(feed::isConnected, 3000);
        assertTrue(feed.isConnected());

        waitUntil(() -> feed.disconnectedCalls.get() > 0, 3000);
        assertTrue(feed.disconnectedCalls.get() > 0, "silent socket should be aborted by the watchdog");
        assertTrue(feed.events.stream().anyMatch(e -> e instanceof MarketDisconnected));

        waitUntil(() -> feed.connectedCalls.get() > 1, 3000);
        assertTrue(feed.connectedCalls.get() > 1, "watchdog abort should flow into the normal reconnect");
        assertTrue(feed.reconnects() > 0);

        feed.tearDown();
    }

    @Test
    void watchdogStaysQuietWhileTextFramesFlow() throws Exception {
        int port = freePort();
        MockWsMarketDataPublisher venue = new MockWsMarketDataPublisher();
        venue.setName("busyVenue");
        venue.setPort(port);
        venue.setMarketDataBookConfigs(List.of(config("ABC-1")));
        venue.ensureStarted();

        PushOnlyFeed feed = new PushOnlyFeed();
        feed.setFeedName("busyFeed");
        feed.setUrl("ws://localhost:" + port + "/");
        feed.setStaleConnectionMillis(300);
        feed.connect();

        waitUntil(feed::isConnected, 3000);
        waitUntil(() -> venue.clientCount() > 0, 2000);
        // stream a book every 50ms for well over the stale limit; the link is never silent
        for (int i = 0; i < 20; i++) {
            venue.timerTriggered();
            Thread.sleep(50);
        }

        assertTrue(feed.messagesReceived() > 0, "books should have arrived");
        assertEquals(0, feed.disconnectedCalls.get(), "watchdog must not abort a live link");
        assertTrue(feed.isConnected());
        assertEquals(0, feed.reconnects());

        feed.tearDown();
    }

    @Test
    void venuePingsKeepAQuietLinkAlive() throws Exception {
        int port = freePort();
        MockWsMarketDataPublisher venue = new MockWsMarketDataPublisher();
        venue.setName("pingVenue");
        venue.setPort(port);
        venue.setMarketDataBookConfigs(List.of(config("ABC-1")));
        venue.ensureStarted(); // never triggered: no text frames, only protocol pings

        PushOnlyFeed feed = new PushOnlyFeed();
        feed.setFeedName("pingFeed");
        feed.setUrl("ws://localhost:" + port + "/");
        feed.setStaleConnectionMillis(300);
        feed.connect();

        waitUntil(feed::isConnected, 3000);
        waitUntil(() -> venue.clientCount() > 0, 2000);
        long openedAt = feed.lastMessageTime();
        for (int i = 0; i < 20; i++) {
            venue.pingClients();
            Thread.sleep(50);
        }

        assertEquals(0, feed.messagesReceived(), "no text frames were sent");
        assertTrue(feed.lastMessageTime() > openedAt, "a ping should count as liveness");
        assertEquals(0, feed.disconnectedCalls.get(), "watchdog must treat venue pings as liveness");
        assertTrue(feed.isConnected());

        feed.tearDown();
    }

    @Test
    void adminDisconnectNotifiesGraphAndSuspendsReconnectUntilConnect() throws Exception {
        int port = freePort();
        MockWsMarketDataPublisher venue = newVenue("adminVenue", port, "ABC-1");

        PushOnlyFeed feed = new PushOnlyFeed();
        feed.setFeedName("adminFeed");
        feed.setUrl("ws://localhost:" + port + "/");
        feed.setReconnectMillis(100);
        Map<String, AdminFunction<String, String>> commands = registerCommands(feed);
        feed.connect();
        waitUntil(feed::isConnected, 3000);
        waitUntil(() -> venue.clientCount() == 1, 2000);

        commands.get("adminFeed.disconnect").processAdminCommand(List.of("adminFeed.disconnect"), out -> { }, err -> {
            throw new AssertionError(err);
        });

        assertFalse(feed.isConnected());
        assertFalse(feed.isReconnectEnabled());
        assertEquals(1, feed.disconnectedCalls.get(), "admin disconnect must fire onDisconnected once");
        assertEquals(1, count(feed, MarketDisconnected.class), "admin disconnect must publish MarketDisconnected");
        waitUntil(() -> venue.clientCount() == 0, 2000);
        assertEquals(0, venue.clientCount(), "venue should see the client leave");

        Thread.sleep(400); // several reconnect intervals
        assertFalse(feed.isConnected(), "feed must stay down after an admin disconnect");
        assertEquals(1, feed.connectedCalls.get(), "no automatic reconnect after an admin disconnect");
        assertEquals(0, feed.reconnects());
        assertEquals(1, feed.disconnectedCalls.get(), "the venue's close ack must not be treated as a second drop");

        commands.get("adminFeed.connect").processAdminCommand(List.of("adminFeed.connect"), out -> { }, err -> {
            throw new AssertionError(err);
        });
        waitUntil(() -> feed.connectedCalls.get() == 2, 3000);
        assertTrue(feed.isConnected(), "explicit connect should bring the feed back");
        assertTrue(feed.isReconnectEnabled());

        feed.tearDown();
    }

    @Test
    void adminReconnectReplacesTheSocketWithoutDisturbingTheNewOne() throws Exception {
        int port = freePort();
        MockWsMarketDataPublisher venue = newVenue("reconnVenue", port, "ABC-1");

        PushOnlyFeed feed = new PushOnlyFeed();
        feed.setFeedName("reconnFeed");
        feed.setUrl("ws://localhost:" + port + "/");
        feed.setReconnectMillis(100);
        feed.subscribeToSymbol("reconnFeed", "reconnVenue", "ABC*");
        Map<String, AdminFunction<String, String>> commands = registerCommands(feed);
        feed.connect();
        waitUntil(feed::isConnected, 3000);
        waitUntil(() -> venue.clientCount() == 1, 2000);

        commands.get("reconnFeed.reconnect").processAdminCommand(List.of("reconnFeed.reconnect"), out -> { }, err -> {
            throw new AssertionError(err);
        });

        waitUntil(() -> feed.connectedCalls.get() == 2, 3000);
        assertTrue(feed.isConnected(), "reconnect should open a new socket");
        // let the old socket's close acknowledgement arrive and any spurious reconnect timer fire
        Thread.sleep(400);
        waitUntil(() -> venue.clientCount() == 1, 2000);
        assertTrue(feed.isConnected(), "old socket's close ack must not tear down the new socket");
        assertEquals(2, feed.connectedCalls.get(), "exactly one new socket");
        assertEquals(1, feed.disconnectedCalls.get(), "exactly one disconnect: the deliberate one");
        assertEquals(2, count(feed, MarketConnected.class));
        assertEquals(1, count(feed, MarketDisconnected.class));
        assertEquals(0, feed.reconnects(), "no automatic reconnect should have been triggered");
        assertEquals(1, venue.clientCount(), "venue should hold exactly one live client");

        // the new socket is live: books still flow
        long before = feed.messagesReceived();
        for (int i = 0; i < 20 && feed.messagesReceived() == before; i++) {
            venue.timerTriggered();
            Thread.sleep(40);
        }
        assertTrue(feed.messagesReceived() > before, "books should flow over the replacement socket");

        feed.tearDown();
    }

    @Test
    void stopPublishesMarketDisconnectedAndDoesNotReconnect() throws Exception {
        int port = freePort();
        MockWsMarketDataPublisher venue = newVenue("stopVenue", port, "ABC-1");

        PushOnlyFeed feed = new PushOnlyFeed();
        feed.setFeedName("stopFeed");
        feed.setUrl("ws://localhost:" + port + "/");
        feed.setReconnectMillis(100);
        feed.connect();
        waitUntil(feed::isConnected, 3000);
        waitUntil(() -> venue.clientCount() == 1, 2000);

        feed.stop();

        assertFalse(feed.isConnected());
        assertEquals(1, feed.disconnectedCalls.get(), "stop must fire onDisconnected once");
        assertEquals(1, count(feed, MarketDisconnected.class), "stop must publish MarketDisconnected");
        Thread.sleep(400);
        assertFalse(feed.isConnected(), "no reconnect after stop");
        assertEquals(1, feed.connectedCalls.get());
        assertEquals(1, feed.disconnectedCalls.get(), "the venue's close ack must not be a second drop");
        waitUntil(() -> venue.clientCount() == 0, 2000);
        assertEquals(0, venue.clientCount());

        feed.tearDown();
    }

    private static long count(PushOnlyFeed feed, Class<? extends MarketFeedEvent> type) {
        return feed.events.stream().filter(type::isInstance).count();
    }

    private static MockWsMarketDataPublisher newVenue(String name, int port, String symbol) {
        MockWsMarketDataPublisher venue = new MockWsMarketDataPublisher();
        venue.setName(name);
        venue.setPort(port);
        venue.setMarketDataBookConfigs(List.of(config(symbol)));
        venue.ensureStarted();
        return venue;
    }

    @Test
    void subscriptionSetIsSafeToIterateWhileMutated() throws Exception {
        // isSubscribed() runs per frame on the WS thread while subscribe/unsubscribe run elsewhere:
        // the base set must tolerate concurrent iteration and mutation without throwing.
        PushOnlyFeed feed = new PushOnlyFeed();
        feed.setFeedName("concurrentFeed");
        feed.subscribeToSymbol("concurrentFeed", "v", "ABC*");
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread mutator = new Thread(() -> {
            try {
                for (int i = 0; i < 20_000; i++) {
                    feed.subscribeToSymbol("concurrentFeed", "v", "SYM-" + (i % 50));
                    feed.unsubscribe("concurrentFeed", "v", "SYM-" + ((i + 25) % 50));
                }
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        mutator.start();
        try {
            for (int i = 0; i < 200_000 && mutator.isAlive(); i++) {
                feed.isSubscribed("ABC-1");   // prefix branch iterates the set
                feed.isSubscribed("ZZZ-" + i);
            }
        } catch (Throwable t) {
            failure.set(t);
        }
        mutator.join(10_000);
        assertNull(failure.get(), "concurrent iteration/mutation must not throw");
        assertTrue(feed.isSubscribed("ABC-1"));
    }

    /** Capture the admin commands the feed registers, keyed by name. */
    private static Map<String, AdminFunction<String, String>> registerCommands(AbstractWsMarketDataFeed feed) {
        Map<String, AdminFunction<String, String>> commands = new HashMap<>();
        feed.adminClient(new AdminCommandRegistry() {
            @Override
            @SuppressWarnings("unchecked")
            public <OUT, ERR> void registerCommand(String name, AdminFunction<OUT, ERR> command) {
                commands.put(name, (AdminFunction<String, String>) command);
            }

            @Override
            public void processAdminCommandRequest(AdminCommandRequest request) {
            }

            @Override
            public List<String> commandList() {
                return List.copyOf(commands.keySet());
            }
        });
        return commands;
    }

    private static MarketDataBookConfig config(String symbol) {
        MarketDataBookConfig c = new MarketDataBookConfig();
        c.setSymbol(symbol);
        c.setFeedName("hooksVenue");
        c.setVenueName("hooksVenue");
        c.setMinPrice(19.9);
        c.setMaxPrice(20.1);
        c.setMinSpread(0.001);
        c.setMaxSpread(0.005);
        c.setMinVolume(10);
        c.setMaxVolume(1000);
        c.setPublishProbability(1.0);
        c.setMultilevel(false);
        return c;
    }

    private static int freePort() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private static void waitUntil(java.util.function.BooleanSupplier cond, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (!cond.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
    }
}
