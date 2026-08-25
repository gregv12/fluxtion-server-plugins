package com.fluxtion.server.plugin.trading.service.quotestream;

/**
 * Sealed union of the outcomes of a live-quote discovery poll (a producer asking the
 * downstream service which {@code (account, symbol)} pairs currently want a live quote
 * stream). Modelled as a sealed type so a dispatch strategy switches exhaustively with
 * no {@code default} — a new outcome forces a compile error at the switch, not a silent
 * drop. Twin of {@code MarketFeedEvent} on the market-data side.
 *
 * <ul>
 *   <li>{@link LiveQuoteSnapshot} — a real response (may be empty); drives diff/cancel.</li>
 *   <li>{@link LiveQuoteNoData}   — "no data yet" / transport error; a HOLD signal, must NOT cancel.</li>
 * </ul>
 */
public sealed interface QuoteRequestEvent permits LiveQuoteSnapshot, LiveQuoteNoData {
}