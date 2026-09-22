package com.fluxtion.server.plugin.trading.component.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxtion.server.plugin.trading.service.marketdata.MarketConnected;
import com.fluxtion.server.plugin.trading.service.marketdata.MarketDataBook;
import com.fluxtion.server.plugin.trading.service.marketdata.MarketDisconnected;
import com.fluxtion.server.plugin.trading.service.marketdata.MarketFeedEvent;
import com.fluxtion.server.plugin.trading.service.marketdata.MultilevelMarketDataBook;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class WsMarketDataMessageTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void bookRoundTripsThroughJsonAndMapsToDomain() throws Exception {
        MarketDataBook src = new MarketDataBook("wsFeed", "wsVenue", "USD-MXN", 42,
                19.98, 100.0, 20.02, 120.0);
        src.setBidOrderCount(3);
        src.setAskOrderCount(4);

        String json = mapper.writeValueAsString(WsMarketDataMessage.fromBook(src));
        // NON_NULL keeps single-level frames free of depth arrays
        assertFalse(json.contains("\"bids\""), json);

        WsMarketDataMessage back = mapper.readValue(json, WsMarketDataMessage.class);
        MarketFeedEvent event = back.toEvent();

        MarketDataBook book = assertInstanceOf(MarketDataBook.class, event);
        assertEquals("wsFeed", book.getFeedName());
        assertEquals("USD-MXN", book.getSymbol());
        assertEquals(42, book.getId());
        assertEquals(19.98, book.getBidPrice());
        assertEquals(100.0, book.getBidQuantity());
        assertEquals(20.02, book.getAskPrice());
        assertEquals(3, book.getBidOrderCount());
        assertEquals(4, book.getAskOrderCount());
    }

    @Test
    void multilevelRoundTripsAndRebuildsBestBidAsk() throws Exception {
        WsMarketDataMessage m = new WsMarketDataMessage();
        m.setType(WsMarketDataMessage.TYPE_MULTILEVEL);
        m.setFeed("wsFeed");
        m.setVenue("wsVenue");
        m.setSymbol("USD-MXN");
        m.setId(7);
        m.setBids(List.of(new WsMarketDataMessage.Level(20.00, 10, 1),
                new WsMarketDataMessage.Level(19.99, 20, 2)));
        m.setAsks(List.of(new WsMarketDataMessage.Level(20.01, 15, 1),
                new WsMarketDataMessage.Level(20.02, 25, 2)));

        String json = mapper.writeValueAsString(m);
        WsMarketDataMessage back = mapper.readValue(json, WsMarketDataMessage.class);

        MultilevelMarketDataBook book = assertInstanceOf(MultilevelMarketDataBook.class, back.toEvent());
        assertEquals(20.00, book.getBestBid().getPrice()); // highest bid
        assertEquals(20.01, book.getBestAsk().getPrice()); // lowest ask
        assertEquals(2, book.getBidDepth());
        assertEquals(2, book.getAskDepth());
    }

    @Test
    void connectedAndDisconnectedMapToDomain() {
        assertInstanceOf(MarketConnected.class, WsMarketDataMessage.connected("wsFeed").toEvent());
        MarketDisconnected d = assertInstanceOf(MarketDisconnected.class,
                WsMarketDataMessage.disconnected("wsFeed").toEvent());
        assertEquals("wsFeed", d.name());
    }

    @Test
    void subscribeIsNotAMarketEvent() throws Exception {
        WsMarketDataMessage sub = WsMarketDataMessage.subscribe("USD-MXN");
        String json = mapper.writeValueAsString(sub);
        assertEquals("USD-MXN", mapper.readValue(json, WsMarketDataMessage.class).getSymbol());
        assertNull(sub.toEvent());
    }

    @Test
    void unsubscribeIsAControlFrame_notAMarketEvent() throws Exception {
        WsMarketDataMessage unsub = WsMarketDataMessage.unsubscribe("USD-BRL");
        assertEquals(WsMarketDataMessage.TYPE_UNSUBSCRIBE, unsub.getType());

        String json = mapper.writeValueAsString(unsub);
        WsMarketDataMessage back = mapper.readValue(json, WsMarketDataMessage.class);
        assertEquals("USD-BRL", back.getSymbol());
        assertEquals(WsMarketDataMessage.TYPE_UNSUBSCRIBE, back.getType());
        // A client->venue control frame maps to no domain market event on either side.
        assertNull(unsub.toEvent());
        assertNull(back.toEvent());
    }
}
