package com.fluxtion.server.plugin.trading.service.quotestream;

/**
 * Per-symbol pricing config for indicative quotes — the maker's skew parameters. A producer computes
 * the skewed base from the live book (which it already has) + this config, so nothing hot (no
 * pre-computed skewed price) crosses a thread boundary; the config itself arrives as
 * {@link PricingConfigEvent}s.
 *
 * @param traderSpreadBps          spread in bps applied symmetrically (bid −half, ask +half) before skew
 * @param bidSkewBps               bid-side skew in bps applied after the spread adjustment
 * @param askSkewBps               ask-side skew in bps applied after the spread adjustment
 * @param spreadStrategy           whether the raw base is the side's top-of-book or the book mid
 * @param priceDecimalPlaces       rounding precision for the wire price
 * @param useWorstPrice            {@code true}=WORST quantity-adjusted pricing, {@code false}=AVERAGE
 * @param quantityAdjustedPricing  whether to apply the per-quantity hedge estimate
 */
public record IndicativePricingConfig(double traderSpreadBps,
                                      double bidSkewBps,
                                      double askSkewBps,
                                      SpreadStrategy spreadStrategy,
                                      int priceDecimalPlaces,
                                      boolean useWorstPrice,
                                      boolean quantityAdjustedPricing) {

    /** No spread, no skew, top-of-book, 5 dp, quantity-adjusted — used when a symbol has no config. */
    public static final IndicativePricingConfig DEFAULT =
            new IndicativePricingConfig(0d, 0d, 0d, SpreadStrategy.TOP_OF_BOOK, 5, false, true);
}