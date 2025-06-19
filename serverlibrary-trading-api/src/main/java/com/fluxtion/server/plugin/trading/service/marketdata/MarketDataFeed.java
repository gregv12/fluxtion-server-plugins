package com.fluxtion.server.plugin.trading.service.marketdata;

import java.util.Set;

public interface MarketDataFeed {

    default void subscribe(String feedName, String venueName, String symbol) {
        throw new UnsupportedOperationException("multi venue not supported venue:" + venueName + " symbol:" + symbol);
    }

    String feedName();

    default Set<String> aggregatedFeeds() {
        return Set.of(feedName());
    }

    default boolean isAggregatedFeedRegistered(String feedName) {
        return aggregatedFeeds().contains(feedName);
    }

    default Set<String> venues() {
        return Set.of(feedName());
    }

    default boolean isVenueRegistered(String venueName) {
        return venues().contains(venueName);
    }
}
