package com.fluxtion.server.plugin.trading.service.quotestream;

/**
 * Per-customer price-bucket spread change for a pair — the producer's config node applies it so the
 * account's indicative quote is widened by {@code spreadBps}. A {@code spreadBps} of 0 means "no
 * bucket adjustment" (base price).
 */
public record BucketConfigEvent(long account, String symbol, double spreadBps) implements QuoteConfigEvent {
}