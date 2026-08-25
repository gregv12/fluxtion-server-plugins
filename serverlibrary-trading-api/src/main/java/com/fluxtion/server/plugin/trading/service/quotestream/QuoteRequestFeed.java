package com.fluxtion.server.plugin.trading.service.quotestream;

/**
 * Service contract for a live-quote discovery feed — the twin of
 * {@link com.fluxtion.server.plugin.trading.service.marketdata.MarketDataFeed}. The concrete
 * implementation (e.g. an async poller of an external discovery endpoint) lives in a venue/
 * connector library and is bound in the app config; a subscription node injects it via
 * {@code @ServiceRegistered} and calls {@link #subscribe()} to register its processor as a
 * {@link QuoteRequestListener} target.
 */
public interface QuoteRequestFeed {

    /**
     * Register the calling processor to receive {@link QuoteRequestEvent}s. Mirrors
     * {@code MarketDataFeed.subscribe} — a no-op until the feed service is registered, so callers
     * safely invoke it from both {@code @ServiceRegistered} and {@code @Start}.
     */
    void subscribe();
}
