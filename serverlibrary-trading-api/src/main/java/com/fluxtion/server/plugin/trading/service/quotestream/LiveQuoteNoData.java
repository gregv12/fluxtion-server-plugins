package com.fluxtion.server.plugin.trading.service.quotestream;

/**
 * Discovery returned no usable data — "cache not populated yet" or a transport
 * error/timeout. This is a HOLD signal: the subscription node must keep its current
 * active set and issue NO cancels (a "no data yet" is explicitly not the same as an
 * empty snapshot).
 */
public record LiveQuoteNoData() implements QuoteRequestEvent {
}