/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.trading.service.node;

import com.fluxtion.server.plugin.trading.service.marketdata.MarketDataFeed;
import com.fluxtion.server.plugin.trading.service.order.OrderExecutor;
import com.fluxtion.server.service.admin.AdminCommandRegistry;

/**
 * TradeServiceListener defines lifecycle and service discovery hooks for trading-related
 * nodes within a Fluxtion graph. Implementors can react to:
 * - init/start/stop lifecycle stages,
 * - discovery of MarketDataFeed and OrderExecutor services,
 * - registration of an AdminCommandRegistry,
 * - generic events via onEvent(Object), and
 * - periodic compute via calculate().
 */
public interface TradeServiceListener {


    /**
     * Called during node initialization phase.
     */
    default void init() {
    }

    /**
     * Called when the node starts processing.
     */
    default void start() {
    }

    /**
     * Called when the node stops processing.
     */
    default void stop() {
    }

    /**
     * Notification of a MarketDataFeed registration.
     *
     * @param marketDataFeed the market data feed instance
     * @param name           the name of the registered feed
     */
    default void marketFeedRegistered(MarketDataFeed marketDataFeed, String name) {
    }

    /**
     * Notification of an OrderExecutor registration.
     *
     * @param orderExecutor the order executor instance
     * @param serviceName   the name of the registered service
     */
    default void orderExecutorRegistered(OrderExecutor orderExecutor, String serviceName) {
    }

    /**
     * Registers an AdminCommandRegistry for administrative operations.
     *
     * @param adminCommandRegistry the admin command registry instance
     */
    default void adminClient(AdminCommandRegistry adminCommandRegistry) {
    }

    /**
     * Handles incoming events in the graph.
     *
     * @param event the event to process
     * @return true if the event was handled, false otherwise
     */
    default boolean onEvent(Object event) {
        return false;
    }

    /**
     * Performs calculations during the graph processing cycle. Invoked after all events have been processed.
     */
    default void calculate() {
    }

    /**
     * Executes after the main calculation cycle is complete.
     */
    default void postCalculate() {
    }
}
