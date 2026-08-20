package com.fluxtion.server.plugin.trading.service.quotestream;

import java.util.List;

/**
 * All active accounts' indicative quotes for a single symbol, produced on the conflated
 * publish cycle — one event per symbol, all accounts in {@code entries}.
 *
 * @param symbol  the client-facing symbol
 * @param entries one {@link QuoteCqEntry} per active account for this symbol
 */
public record CustomerQuoteEvent(String symbol, List<QuoteCqEntry> entries) {
}