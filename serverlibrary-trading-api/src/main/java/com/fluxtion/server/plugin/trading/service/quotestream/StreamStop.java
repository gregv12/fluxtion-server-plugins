package com.fluxtion.server.plugin.trading.service.quotestream;

/**
 * Signals that an {@code (account, symbol)} pair is no longer live (present in the previous
 * snapshot, absent in the current — including symbols that vanished entirely or an empty
 * response), and once per pair on clean shutdown. A pricer removes it from the active set and
 * emits exactly one cancel for it.
 */
public record StreamStop(long account, String symbol) {
}