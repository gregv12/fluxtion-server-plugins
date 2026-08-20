package com.fluxtion.server.plugin.trading.service.quotestream;

/**
 * Callback a processor exports to receive indicative-quote config from a {@link QuoteConfigFeed}. A
 * feed's invocation strategy dispatches the sealed {@link QuoteConfigEvent} union onto these typed
 * methods; {@code isValidTarget} checks {@code exportsService(QuoteConfigListener.class)}.
 *
 * <p>Return {@code true} when the call changed processor state (so downstream nodes re-evaluate).
 */
public interface QuoteConfigListener {

    /** Per-symbol maker skew config changed. */
    boolean onPricingConfig(PricingConfigEvent event);

    /** Per-customer bucket spread changed. */
    boolean onBucketConfig(BucketConfigEvent event);
}