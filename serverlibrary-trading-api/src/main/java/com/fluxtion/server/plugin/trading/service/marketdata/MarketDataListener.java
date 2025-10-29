package com.fluxtion.server.plugin.trading.service.marketdata;

/**
 * Listener interface for receiving market data updates and connection status changes.
 * Implementations can process market data book updates and handle market venue connectivity events.
 */
public interface MarketDataListener {

    /**
     * Callback for single-level market data book updates.
     *
     * @param marketDataBook the market data book update containing best bid/ask prices
     * @return true if the event was handled, false otherwise
     */
    boolean onMarketData(MarketDataBook marketDataBook);

    /**
     * Callback for multilevel market data book updates.
     *
     * <p>This method is called when a multilevel order book update is received,
     * providing access to full market depth data for market making and trading strategies.</p>
     *
     * @param multilevelBook the multilevel market data book update
     * @return true if the event was handled, false otherwise
     */
    default boolean onMultilevelMarketData(MultilevelMarketDataBook multilevelBook) {
        return false;
    }

    /**
     * Callback when a market data venue connection is established.
     *
     * @param marketConnected the market connection event details
     * @return true if the event was handled, false otherwise
     */
    boolean marketDataVenueConnected(MarketConnected marketConnected);

    /**
     * Callback when a market data venue connection is lost or closed.
     *
     * @param marketDisconnected the market disconnection event details
     * @return true if the event was handled, false otherwise
     */
    boolean marketDataVenueDisconnected(MarketDisconnected marketDisconnected);
}
