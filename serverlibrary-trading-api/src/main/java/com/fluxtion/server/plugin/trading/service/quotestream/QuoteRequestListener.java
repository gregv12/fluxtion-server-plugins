package com.fluxtion.server.plugin.trading.service.quotestream;

/**
 * Callback interface a processor exports to receive live-quote discovery events from a
 * {@link QuoteRequestFeed} — the twin of
 * {@link com.fluxtion.server.plugin.trading.service.marketdata.MarketDataListener}. A feed's
 * invocation strategy dispatches the sealed {@link QuoteRequestEvent} union onto these typed
 * methods; the strategy's {@code isValidTarget} checks
 * {@code exportsService(QuoteRequestListener.class)}.
 *
 * <p>Return {@code true} when the call changed processor state (so downstream nodes are
 * triggered), {@code false} otherwise — {@link #onNoData} always returns {@code false}.
 */
public interface QuoteRequestListener {

    /** A real snapshot — diff against the previous and emit start/stop. */
    boolean onSnapshot(LiveQuoteSnapshot snapshot);

    /** "No data yet" / error — HOLD: keep the active set, emit nothing. */
    boolean onNoData(LiveQuoteNoData noData);
}