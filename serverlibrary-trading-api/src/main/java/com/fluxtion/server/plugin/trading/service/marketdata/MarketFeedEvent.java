package com.fluxtion.server.plugin.trading.service.marketdata;

/**
 * Marker interface for all market data related events in the trading system.
 * This sealed interface restricts its implementations to specific market data event types.
 *
 * <p>Permitted implementations:</p>
 * <ul>
 *     <li>{@link MarketDataBook} - Single level market data book updates</li>
 *     <li>{@link MultilevelMarketDataBook} - Multi-level order book updates</li>
 *     <li>{@link MarketConnected} - Market venue connection established events</li>
 *     <li>{@link MarketDisconnected} - Market venue disconnection events</li>
 * </ul>
 */
public sealed interface MarketFeedEvent permits MarketDataBook, MultilevelMarketDataBook, MarketConnected, MarketDisconnected {
}
