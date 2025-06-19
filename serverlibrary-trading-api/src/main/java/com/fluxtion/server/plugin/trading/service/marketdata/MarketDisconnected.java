package com.fluxtion.server.plugin.trading.service.marketdata;

public record MarketDisconnected(String name) implements MarketFeedEvent {
}
