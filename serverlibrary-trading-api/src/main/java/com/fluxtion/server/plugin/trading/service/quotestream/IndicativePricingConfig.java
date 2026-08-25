package com.fluxtion.server.plugin.trading.service.quotestream;

/**
 * Per-symbol pricing config for indicative quotes — the maker's skew parameters plus the price-source
 * mapping. A producer computes the skewed base from the live book (which it already has) + this config,
 * so nothing hot (no pre-computed skewed price) crosses a thread boundary; the config itself arrives as
 * {@link PricingConfigEvent}s.
 *
 * @param priceSourceInstrument the market-data instrument this quote symbol prices off — the venue's
 *                              {@code QuotableSymbol.priceSourceInstrument}. When {@code null}/blank the
 *                              quote symbol prices off its own book (identity mapping) — the
 *                              backward-compatible default, so existing config with no mapping is
 *                              unchanged. E.g. {@code USDT-MXN} sources from {@code USD-MXN_INTERNAL}.
 * @param traderSpreadBps          spread in bps applied symmetrically (bid −half, ask +half) before skew
 * @param bidSkewBps               bid-side skew in bps applied after the spread adjustment
 * @param askSkewBps               ask-side skew in bps applied after the spread adjustment
 * @param spreadStrategy           whether the raw base is the side's top-of-book or the book mid
 * @param priceDecimalPlaces       rounding precision for the wire price
 * @param useWorstPrice            {@code true}=WORST quantity-adjusted pricing, {@code false}=AVERAGE
 * @param quantityAdjustedPricing  whether to apply the per-quantity hedge estimate
 */
public record IndicativePricingConfig(String priceSourceInstrument,
                                      double traderSpreadBps,
                                      double bidSkewBps,
                                      double askSkewBps,
                                      SpreadStrategy spreadStrategy,
                                      int priceDecimalPlaces,
                                      boolean useWorstPrice,
                                      boolean quantityAdjustedPricing) {

    /**
     * Backward-compatible constructor — no price-source mapping (identity: the quote symbol prices off
     * its own book). Existing callers that predate the mapping compile and behave unchanged.
     */
    public IndicativePricingConfig(double traderSpreadBps,
                                   double bidSkewBps,
                                   double askSkewBps,
                                   SpreadStrategy spreadStrategy,
                                   int priceDecimalPlaces,
                                   boolean useWorstPrice,
                                   boolean quantityAdjustedPricing) {
        this(null, traderSpreadBps, bidSkewBps, askSkewBps, spreadStrategy, priceDecimalPlaces,
                useWorstPrice, quantityAdjustedPricing);
    }

    /** No spread, no skew, top-of-book, 5 dp, quantity-adjusted, identity price source — the fallback. */
    public static final IndicativePricingConfig DEFAULT =
            new IndicativePricingConfig(0d, 0d, 0d, SpreadStrategy.TOP_OF_BOOK, 5, false, true);
}
