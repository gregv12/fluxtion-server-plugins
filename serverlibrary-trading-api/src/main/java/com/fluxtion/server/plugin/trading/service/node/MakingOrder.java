/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.trading.service.node;

/**
 * Represents a making order in a trading system that can be modified or cancelled.
 * A making order provides liquidity to the market by placing limit orders that are not immediately executed.
 */
public interface MakingOrder {
    /**
     * Modifies the existing order with new quantity and price parameters.
     *
     * @param quantity the new quantity for the order
     * @param price    the new price for the order
     */
    void modify(double quantity, double price);

    /**
     * Cancels the current order, removing it from the order book.
     */
    void cancelOrder();

    /**
     * Checks if the order has been closed either through execution, cancellation, or rejection.
     *
     * @return true if the order is closed, false if it is still active
     */
    boolean isOrderClosed();
}
