package com.fluxtion.server.plugin.trading.service.quotestream;

/**
 * Service contract for a feed that publishes indicative-quote config changes as
 * {@link QuoteConfigEvent}s. The concrete implementation (sourcing config from a cache, admin or
 * file) lives in a venue/connector library and is bound in the app config; a config node injects it
 * via {@code @ServiceRegistered} and calls {@link #subscribe()} to register as a
 * {@link QuoteConfigListener} target. Twin of {@code QuoteRequestFeed} on the discovery side.
 */
public interface QuoteConfigFeed {

    /** Register the calling processor to receive {@link QuoteConfigEvent}s (no-op until registered). */
    void subscribe();

    /**
     * Producer-side push: a config owner (e.g. a venue that holds the maker skew) publishes a
     * per-symbol pricing config. Thread-safe hand-off onto the feed's queue — the subscribed
     * processor drains it on its agent thread.
     */
    void publishPricingConfig(String symbol, IndicativePricingConfig config);

    /** Producer-side push: a per-customer bucket spread for a pair. */
    void publishBucketConfig(long account, String symbol, double spreadBps);
}