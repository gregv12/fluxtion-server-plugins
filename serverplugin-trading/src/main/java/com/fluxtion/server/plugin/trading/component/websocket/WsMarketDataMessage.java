package com.fluxtion.server.plugin.trading.component.websocket;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fluxtion.server.plugin.trading.service.marketdata.MarketConnected;
import com.fluxtion.server.plugin.trading.service.marketdata.MarketDataBook;
import com.fluxtion.server.plugin.trading.service.marketdata.MarketDisconnected;
import com.fluxtion.server.plugin.trading.service.marketdata.MarketFeedEvent;
import com.fluxtion.server.plugin.trading.service.marketdata.MultilevelBookConfig;
import com.fluxtion.server.plugin.trading.service.marketdata.MultilevelMarketDataBook;
import com.fluxtion.server.plugin.trading.service.marketdata.PriceLevel;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * JSON wire message shared by the mock WebSocket venue (server) and the WebSocket feed (client).
 *
 * <p>Deliberately decoupled from the domain {@link MarketFeedEvent} types: {@link MultilevelMarketDataBook}
 * has final fields and private arrays and is not Jackson-round-trippable, so this flat DTO is the
 * serialization boundary and {@link #toEvent()} / the {@code from*} factories own the mapping both ways.</p>
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class WsMarketDataMessage {

    public static final String TYPE_BOOK = "book";
    public static final String TYPE_MULTILEVEL = "multilevel";
    public static final String TYPE_CONNECTED = "connected";
    public static final String TYPE_DISCONNECTED = "disconnected";
    public static final String TYPE_SUBSCRIBE = "subscribe";
    public static final String TYPE_UNSUBSCRIBE = "unsubscribe";

    private String type;
    private String feed;
    private String venue;
    private String symbol;
    private long id;

    // single-level top of book
    private Double bid;
    private Double bidQty;
    private Integer bidOrders;
    private Double ask;
    private Double askQty;
    private Integer askOrders;

    // multilevel depth
    private List<Level> bids;
    private List<Level> asks;

    @Data
    @NoArgsConstructor
    public static class Level {
        private double price;
        private double qty;
        private int orders;

        public Level(double price, double qty, int orders) {
            this.price = price;
            this.qty = qty;
            this.orders = orders;
        }
    }

    // ==================== factories: domain -> wire ====================

    public static WsMarketDataMessage fromBook(MarketDataBook b) {
        WsMarketDataMessage m = base(TYPE_BOOK, b.getFeedName(), b.getVenueName(), b.getSymbol(), b.getId());
        m.bid = b.getBidPrice();
        m.bidQty = b.getBidQuantity();
        m.bidOrders = b.getBidOrderCount();
        m.ask = b.getAskPrice();
        m.askQty = b.getAskQuantity();
        m.askOrders = b.getAskOrderCount();
        return m;
    }

    public static WsMarketDataMessage fromMultilevel(MultilevelMarketDataBook b) {
        WsMarketDataMessage m = base(TYPE_MULTILEVEL, b.getFeedName(), b.getVenueName(), b.getSymbol(), b.getId());
        m.bids = levels(b.getBidLevels());
        m.asks = levels(b.getAskLevels());
        return m;
    }

    public static WsMarketDataMessage connected(String feed) {
        return base(TYPE_CONNECTED, feed, feed, null, 0);
    }

    public static WsMarketDataMessage disconnected(String feed) {
        return base(TYPE_DISCONNECTED, feed, feed, null, 0);
    }

    public static WsMarketDataMessage subscribe(String symbol) {
        WsMarketDataMessage m = new WsMarketDataMessage();
        m.type = TYPE_SUBSCRIBE;
        m.symbol = symbol;
        return m;
    }

    public static WsMarketDataMessage unsubscribe(String symbol) {
        WsMarketDataMessage m = new WsMarketDataMessage();
        m.type = TYPE_UNSUBSCRIBE;
        m.symbol = symbol;
        return m;
    }

    // ==================== wire -> domain ====================

    /**
     * Map this message to a {@link MarketFeedEvent}, or null if it is not a market event
     * (e.g. a client subscribe or an unknown type).
     */
    public MarketFeedEvent toEvent() {
        if (type == null) {
            return null;
        }
        switch (type) {
            case TYPE_BOOK: {
                MarketDataBook book = new MarketDataBook(feed, venue, symbol, id,
                        d(bid), d(bidQty), d(ask), d(askQty));
                if (bidOrders != null) book.setBidOrderCount(bidOrders);
                if (askOrders != null) book.setAskOrderCount(askOrders);
                return book;
            }
            case TYPE_MULTILEVEL: {
                int depth = Math.min(1000, Math.max(20,
                        Math.max(size(bids), size(asks))));
                MultilevelMarketDataBook book = new MultilevelMarketDataBook(feed, venue, symbol, id,
                        MultilevelBookConfig.builder().maxDepth(depth).build());
                if (bids != null) {
                    for (Level l : bids) {
                        if (l.price > 0.0 && l.qty > 0.0) book.updateBid(l.price, l.qty, l.orders);
                    }
                }
                if (asks != null) {
                    for (Level l : asks) {
                        if (l.price > 0.0 && l.qty > 0.0) book.updateAsk(l.price, l.qty, l.orders);
                    }
                }
                return book;
            }
            case TYPE_CONNECTED:
                return new MarketConnected(feed);
            case TYPE_DISCONNECTED:
                return new MarketDisconnected(feed);
            default:
                return null; // subscribe / unknown
        }
    }

    private static WsMarketDataMessage base(String type, String feed, String venue, String symbol, long id) {
        WsMarketDataMessage m = new WsMarketDataMessage();
        m.type = type;
        m.feed = feed;
        m.venue = venue;
        m.symbol = symbol;
        m.id = id;
        return m;
    }

    private static List<Level> levels(List<PriceLevel> src) {
        List<Level> out = new ArrayList<>(src.size());
        for (PriceLevel p : src) {
            out.add(new Level(p.getPrice(), p.getQuantity(), p.getOrderCount()));
        }
        return out;
    }

    private static int size(List<Level> l) {
        return l == null ? 0 : l.size();
    }

    private static double d(Double v) {
        return v == null ? 0.0 : v;
    }
}
