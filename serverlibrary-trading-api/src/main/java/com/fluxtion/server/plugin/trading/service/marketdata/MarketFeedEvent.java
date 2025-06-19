package com.fluxtion.server.plugin.trading.service.marketdata;

public sealed interface MarketFeedEvent permits MarketDataBook, MarketConnected, MarketDisconnected {
}
