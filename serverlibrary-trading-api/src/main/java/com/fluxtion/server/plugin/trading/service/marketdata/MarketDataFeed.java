package com.fluxtion.server.plugin.trading.service.marketdata;

import java.util.Set;

public interface MarketDataFeed {

    default void subscribe(String feedName, String venueName, String symbol) {
        throw new UnsupportedOperationException("multi venue not supported venue:" + venueName + " symbol:" + symbol);
    }

    /**
     * Stop a live subscription for a symbol. Default is a no-op: feeds whose transport supports
     * removing a subscription (e.g. a WebSocket venue) override this to send the unsubscribe and
     * drop the symbol; feeds without an unsubscribe protocol simply ignore it.
     */
    default void unsubscribe(String feedName, String venueName, String symbol) {
        // no-op by default
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
