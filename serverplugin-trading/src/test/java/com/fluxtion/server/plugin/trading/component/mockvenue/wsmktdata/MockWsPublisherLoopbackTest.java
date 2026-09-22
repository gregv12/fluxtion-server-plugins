package com.fluxtion.server.plugin.trading.component.mockvenue.wsmktdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxtion.server.plugin.trading.component.mockvenue.mktdata.MarketDataBookConfig;
import com.fluxtion.server.plugin.trading.component.websocket.WsMarketDataMessage;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockWsPublisherLoopbackTest {

    @Test
    void clientConnectsAndReceivesBroadcastBook() throws Exception {
        int port = freePort();

        MockWsMarketDataPublisher venue = new MockWsMarketDataPublisher();
        venue.setName("testWsVenue");
        venue.setPort(port);
        venue.setMarketDataBookConfigs(List.of(singleLevelConfig("USD-MXN")));
        venue.ensureStarted();

        CopyOnWriteArrayList<String> received = new CopyOnWriteArrayList<>();
        CountDownLatch gotBook = new CountDownLatch(1);

        WebSocket ws = HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + port + "/"), new WebSocket.Listener() {
                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        received.add(data.toString());
                        gotBook.countDown();
                        webSocket.request(1);
                        return null;
                    }
                })
                .get(5, TimeUnit.SECONDS);

        // wait until the server side has registered the client, then drive one publish tick
        waitUntil(() -> venue.clientCount() > 0, 2000);
        for (int i = 0; i < 20 && gotBook.getCount() > 0; i++) {
            venue.timerTriggered();
            if (gotBook.await(100, TimeUnit.MILLISECONDS)) {
                break;
            }
        }

        assertTrue(gotBook.await(2, TimeUnit.SECONDS), "expected a broadcast book");
        WsMarketDataMessage msg = new ObjectMapper().readValue(received.get(0), WsMarketDataMessage.class);
        assertEquals(WsMarketDataMessage.TYPE_BOOK, msg.getType());
        assertEquals("USD-MXN", msg.getSymbol());
        assertTrue(msg.getBid() > 0.0 && msg.getAsk() > 0.0, "book should carry prices");

        ws.abort();
    }

    @Test
    void requireSubscription_streamsAfterSubscribe_andStopsAfterUnsubscribe() throws Exception {
        int port = freePort();

        MockWsMarketDataPublisher venue = new MockWsMarketDataPublisher();
        venue.setName("gatedWsVenue");
        venue.setPort(port);
        venue.setRequireSubscription(true); // real-venue behaviour: only stream subscribed symbols
        venue.setMarketDataBookConfigs(List.of(singleLevelConfig("USD-MXN")));
        venue.ensureStarted();

        CopyOnWriteArrayList<String> received = new CopyOnWriteArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        WebSocket ws = HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + port + "/"), new WebSocket.Listener() {
                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        received.add(data.toString());
                        webSocket.request(1);
                        return null;
                    }
                })
                .get(5, TimeUnit.SECONDS);

        waitUntil(() -> venue.clientCount() > 0, 2000);

        // before any subscribe the gate suppresses everything
        for (int i = 0; i < 5; i++) {
            venue.timerTriggered();
            Thread.sleep(20);
        }
        assertTrue(received.isEmpty(), "nothing should stream before a subscribe");

        // subscribe -> venue registers the symbol -> ticks flow
        ws.sendText(mapper.writeValueAsString(WsMarketDataMessage.subscribe("USD-MXN")), true);
        waitUntil(() -> venue.subscribedSymbols().contains("USD-MXN"), 2000);
        assertTrue(venue.subscribedSymbols().contains("USD-MXN"), "venue should register the subscription");
        for (int i = 0; i < 20 && received.isEmpty(); i++) {
            venue.timerTriggered();
            Thread.sleep(30);
        }
        assertFalse(received.isEmpty(), "a subscribed symbol should stream");

        // unsubscribe -> venue drops the symbol -> gate stops further ticks
        ws.sendText(mapper.writeValueAsString(WsMarketDataMessage.unsubscribe("USD-MXN")), true);
        waitUntil(() -> !venue.subscribedSymbols().contains("USD-MXN"), 2000);
        assertFalse(venue.subscribedSymbols().contains("USD-MXN"), "venue should drop the subscription");

        received.clear();
        for (int i = 0; i < 10; i++) {
            venue.timerTriggered();
            Thread.sleep(30);
        }
        assertTrue(received.isEmpty(), "no ticks should be delivered after unsubscribe");

        ws.abort();
    }

    private static MarketDataBookConfig singleLevelConfig(String symbol) {
        MarketDataBookConfig c = new MarketDataBookConfig();
        c.setSymbol(symbol);
        c.setFeedName("testWsVenue");
        c.setVenueName("testWsVenue");
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
