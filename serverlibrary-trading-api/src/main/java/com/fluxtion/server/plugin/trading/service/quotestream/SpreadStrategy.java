package com.fluxtion.server.plugin.trading.service.quotestream;

/**
 * How the spread-adjusted base is derived from the book, mirroring a maker venue's skew strategy:
 * <ul>
 *   <li>{@link #TOP_OF_BOOK} — adjust from the side's best price (best bid for bid, best ask for ask);</li>
 *   <li>{@link #MID} — adjust from the book mid ((bid+ask)/2) on both sides.</li>
 * </ul>
 */
public enum SpreadStrategy {
    MID,
    TOP_OF_BOOK
}