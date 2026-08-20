package com.fluxtion.server.plugin.trading.service.quotestream;

/**
 * One account's two-sided indicative quote within a {@link CustomerQuoteEvent}. A concrete
 * publisher maps a list of these onto its wire format. A {@code NaN} price on a side means
 * "no quotable price on that side" and the publisher should omit/skip that side rather than
 * encode a garbage value.
 */
public record QuoteCqEntry(long account,
                           double bidPrice,
                           double askPrice,
                           QtyBucket bidQuantityBucket,
                           QtyBucket askQuantityBucket) {
}