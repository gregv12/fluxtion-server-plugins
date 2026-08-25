package com.fluxtion.server.plugin.trading.service.quotestream;

/**
 * Quantity bucket an indicative quote is priced at. Transport-neutral: a concrete
 * publisher maps it to whatever wire enum its downstream requires. {@link #notional()}
 * is the representative quantity used for quantity-adjusted (hedge-estimate) pricing.
 */
public enum QtyBucket {
    BUCKET_1_000(1_000d),
    BUCKET_10_000(10_000d),
    BUCKET_100_000(100_000d),
    BUCKET_1_000_000(1_000_000d);

    private final double notional;

    QtyBucket(double notional) {
        this.notional = notional;
    }

    /** The representative notional for this bucket, used for quantity-adjusted pricing. */
    public double notional() {
        return notional;
    }
}