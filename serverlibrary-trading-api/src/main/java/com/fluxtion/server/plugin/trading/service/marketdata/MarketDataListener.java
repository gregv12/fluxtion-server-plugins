package com.fluxtion.server.plugin.trading.service.marketdata;

public interface MarketDataListener {

    boolean onMarketData(MarketDataBook marketDataBook);

    boolean marketDataVenueConnected(MarketConnected marketConnected);

    boolean marketDataVenueDisconnected(MarketDisconnected marketDisconnected);
}
