package com.fluxtion.server.plugin.trading.service.quotestream;

/**
 * Cancels the live indicative quote for exactly one {@code (account, symbol)} pair. Cancels
 * are never conflated and must be delivered reliably (retried until they land) — a missed
 * cancel leaves a stale quote visible to clients.
 */
public record CustomerQuoteCancelEvent(long account, String symbol) {
}