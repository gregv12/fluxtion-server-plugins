/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.trading.service.node;

import com.fluxtion.server.plugin.trading.service.marketdata.MarketDataBook;

/**
 * Subscriber interface for receiving and managing market data book updates.
 * Implementations of this interface handle market data subscriptions and provide
 * access to market data book information for specific trading venues and symbols.
 */
public interface MarketDataBookSubscriber {
    /**
     * Returns the name of the subscriber instance.
     *
     * @return the subscriber name
     */
    String getName();

    /**
     * Returns the name of the market data feed this subscriber is connected to.
     *
     * @return the market data feed name
     */
    String getFeedName();

    /**
     * Returns the name of the trading venue this subscriber is monitoring.
     *
     * @return the venue name
     */
    String getVenueName();

    /**
     * Returns the trading symbol this subscriber is subscribed to.
     *
     * @return the subscribed symbol
     */
    String getSubscribeSymbol();

    /**
     * Returns the current market data book for the subscribed symbol.
     *
     * @return the current market data book
     */
    MarketDataBook getMarketDataBook();

    /**
     * Indicates whether the market data book has been updated since last check.
     *
     * @return true if the market data book has been updated, false otherwise
     */
    boolean isUpdated();
}
