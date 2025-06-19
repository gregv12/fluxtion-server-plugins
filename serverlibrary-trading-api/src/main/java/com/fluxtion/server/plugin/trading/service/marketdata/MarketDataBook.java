package com.fluxtion.server.plugin.trading.service.marketdata;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public final class MarketDataBook implements MarketFeedEvent {
    private String feedName;
    private String venueName;
    private String symbol;
    private long id;

    private double bidPrice;
    private double bidQuantity;
    private int bidOrderCount;

    private double askPrice;
    private double askQuantity;
    private int askOrderCount;

    public MarketDataBook(String symbol, long id) {
        this.symbol = symbol;
        this.id = id;
        bidOrderCount = 0;
        askOrderCount = 0;
    }

    public MarketDataBook(String symbol, long id, double bidPrice, double bidQuantity, double askPrice, double askQuantity) {
        this.symbol = symbol;
        this.id = id;
        this.bidPrice = bidPrice;
        this.bidQuantity = bidQuantity;
        this.askPrice = askPrice;
        this.askQuantity = askQuantity;
        bidOrderCount = 1;
        askOrderCount = 1;
    }

    public MarketDataBook(String feedName, String venueName, String symbol, long id, double bidPrice, double bidQuantity, double askPrice, double askQuantity) {
        this.feedName = feedName;
        this.venueName = venueName;
        this.symbol = symbol;
        this.id = id;
        this.bidPrice = bidPrice;
        this.bidQuantity = bidQuantity;
        this.askPrice = askPrice;
        this.askQuantity = askQuantity;
        bidOrderCount = 1;
        askOrderCount = 1;
    }
}
