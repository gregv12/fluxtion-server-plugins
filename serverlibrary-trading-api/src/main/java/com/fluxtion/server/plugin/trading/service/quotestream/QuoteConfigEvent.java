package com.fluxtion.server.plugin.trading.service.quotestream;

/**
 * Sealed union of indicative-quote config changes delivered as events (so config is part of the
 * event stream → replayable), dispatched exhaustively to a {@link QuoteConfigListener}:
 * <ul>
 *   <li>{@link PricingConfigEvent} — per-symbol maker skew config;</li>
 *   <li>{@link BucketConfigEvent}  — per-customer price-bucket spread.</li>
 * </ul>
 */
public sealed interface QuoteConfigEvent permits PricingConfigEvent, BucketConfigEvent {
}