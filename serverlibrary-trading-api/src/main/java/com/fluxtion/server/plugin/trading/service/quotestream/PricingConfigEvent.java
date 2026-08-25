package com.fluxtion.server.plugin.trading.service.quotestream;

/**
 * Per-symbol pricing config change — the producer's config node applies it to price {@code symbol}.
 */
public record PricingConfigEvent(String symbol, IndicativePricingConfig config) implements QuoteConfigEvent {
}