package com.fluxtion.server.plugin.trading.component.websocket;

import com.fluxtion.server.plugin.trading.component.mockvenue.mktdata.MarketDataBookConfig;
import com.fluxtion.server.plugin.trading.component.mockvenue.wsmktdata.MockWsMarketDataPublisher;
import com.fluxtion.server.plugin.trading.service.marketdata.MarketConnected;
import com.fluxtion.server.plugin.trading.service.marketdata.MarketDataBook;
import com.fluxtion.server.plugin.trading.service.marketdata.MarketFeedEvent;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end: the mock WS venue (server) drives the real {@link GenericJsonWsMarketDataFeed} (client)
 * over a genuine WebSocket, and we assert the feed emits the correct {@link MarketFeedEvent}s. The
 * feed's {@code publish(...)} is captured by a test subclass (the framework's targetQueue is not
 * wired here — that path is exercised in the deployment run + the strategy test).
 */
class WsMarketDataFeedIntegrationTest {

    static final class CapturingFeed extends GenericJsonWsMarketDataFeed {
        final CopyOnWriteArrayList<MarketFeedEvent> events = new CopyOnWriteArrayList<>();

        @Override
        protected void publish(MarketFeedEvent event) {
            events.add(event); // do NOT call super: no EventFlowManager wired in this unit test
        }

        /** expose the protected subscription set for assertions */
        java.util.Set<String> subs() {
            return subscriptions;
        }
    }

    @Test
    void feedConnectsSubscribesAndEmitsBooksFromTheVenue() throws Exception {
        int port = freePort();

        MockWsMarketDataPublisher venue = new MockWsMarketDataPublisher();
        venue.setName("itVenue");
        venue.setPort(port);
        venue.setMarketDataBookConfigs(List.of(config("USD-MXN")));
        venue.ensureStarted();

        CapturingFeed feed = new CapturingFeed();
        feed.setFeedName("wsIntegFeed");
        feed.setUrl("ws://localhost:" + port + "/");
        feed.subscribeToSymbol("wsIntegFeed", "itVenue", "USD-MXN");
        feed.connect();

        waitUntil(feed::isConnected, 3000);
        assertTrue(feed.isConnected(), "feed should connect to the venue");

        // drive publishes until the feed has emitted a book
        waitUntil(() -> venue.clientCount() > 0, 2000);
        for (int i = 0; i < 50 && !hasBook(feed.events); i++) {
            venue.timerTriggered();
            Thread.sleep(40);
        }

        assertTrue(feed.events.stream().anyMatch(e -> e instanceof MarketConnected),
                "feed should emit MarketConnected on open");
        MarketDataBook book = feed.events.stream()
                .filter(e -> e instanceof MarketDataBook).map(e -> (MarketDataBook) e)
                .findFirst().orElseThrow(() -> new AssertionError("no MarketDataBook emitted"));
        assertEquals("USD-MXN", book.getSymbol());
        assertTrue(book.getBidPrice() > 0.0 && book.getAskPrice() > 0.0);
        assertTrue(feed.messagesReceived() > 0);

        feed.tearDown();
    }

    @Test
    void unsubscribePublishesEmptyBookAndDropsSubscription() {
        CapturingFeed feed = new CapturingFeed();
        feed.setFeedName("wsUnsubFeed");
        // no connection needed: unsubscribe() drops the local subscription and publishes the
        // end-of-subscription empty book synchronously; the wire frame is only sent when connected.
        feed.subscribeToSymbol("wsUnsubFeed", "wsUnsubFeed", "USD-BRL");
        assertTrue(feed.subs().contains("USD-BRL"), "subscription should be recorded");

        feed.unsubscribe("wsUnsubFeed", "wsUnsubFeed", "USD-BRL");

        assertFalse(feed.subs().contains("USD-BRL"), "unsubscribe should drop the local subscription");

        MarketDataBook empty = feed.events.stream()
                .filter(e -> e instanceof MarketDataBook).map(e -> (MarketDataBook) e)
                .reduce((first, second) -> second) // last book published
                .orElseThrow(() -> new AssertionError("unsubscribe should publish an empty book"));
        assertEquals("USD-BRL", empty.getSymbol());
        assertEquals(0.0, empty.getBidPrice(), "empty book bid should be 0");
        assertEquals(0.0, empty.getAskPrice(), "empty book ask should be 0");
        assertEquals(0.0, empty.getBidQuantity());
        assertEquals(0.0, empty.getAskQuantity());
        assertEquals(0, empty.getBidOrderCount(), "empty book should carry zero orders");
        assertEquals(0, empty.getAskOrderCount());
    }

    private static boolean hasBook(List<MarketFeedEvent> events) {
        return events.stream().anyMatch(e -> e instanceof MarketDataBook);
    }

    private static MarketDataBookConfig config(String symbol) {
        MarketDataBookConfig c = new MarketDataBookConfig();
        c.setSymbol(symbol);
        c.setFeedName("itVenue");
        c.setVenueName("itVenue");
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
