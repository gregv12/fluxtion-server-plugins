package com.fluxtion.server.plugin.trading.service.quotestream;

/**
 * Signals that an {@code (account, symbol)} pair has become newly live (present in the
 * current discovery snapshot, absent in the previous). A pricer adds it to its active set
 * and begins quoting it on the next publish cycle.
 */
public record StreamStart(long account, String symbol) {
}