package com.fluxtion.server.plugin.trading.service.quotestream;

import java.util.Map;
import java.util.Set;

/**
 * A successful discovery response: the set of accounts that currently want a live stream,
 * keyed by symbol. An empty map is legitimate and means "no pairs are live" — it drives
 * cancellation of every currently-active pair.
 *
 * @param symbolToAccounts symbol → accounts currently requesting a quote for it
 */
public record LiveQuoteSnapshot(Map<String, Set<Long>> symbolToAccounts) implements QuoteRequestEvent {
}