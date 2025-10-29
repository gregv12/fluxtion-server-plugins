/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.trading.service.marketdata;

import lombok.Value;
import lombok.With;

/**
 * Represents a single price level in an order book.
 *
 * <p>This is an immutable value object providing thread-safety and clean event semantics
 * for Fluxtion event processing. Each price level aggregates all orders at a specific
 * price point.</p>
 *
 * <p>For market making systems, price levels are frequently created and updated.
 * Consider object pooling if profiling shows significant GC pressure.</p>
 *
 * @see MultilevelMarketDataBook
 */
@Value
@With
public class PriceLevel {

    /**
     * The price of this level in the order book.
     */
    double price;

    /**
     * The total quantity available at this price level.
     */
    double quantity;

    /**
     * The number of orders at this price level.
     */
    int orderCount;

    /**
     * Creates an empty price level with zero quantity.
     *
     * @param price the price of this level
     * @return a price level with zero quantity and order count
     */
    public static PriceLevel empty(double price) {
        return new PriceLevel(price, 0.0, 0);
    }

    /**
     * Checks if this level has no quantity (is empty).
     *
     * @return true if quantity is zero or negative
     */
    public boolean isEmpty() {
        return quantity <= 0.0;
    }

    /**
     * Validates this price level has valid values.
     *
     * @return true if price is positive and quantity/order count are non-negative
     */
    public boolean isValid() {
        return price > 0.0 && quantity >= 0.0 && orderCount >= 0;
    }

    @Override
    public String toString() {
        return String.format("PriceLevel[price=%.4f, qty=%.4f, orders=%d]",
                           price, quantity, orderCount);
    }
}
