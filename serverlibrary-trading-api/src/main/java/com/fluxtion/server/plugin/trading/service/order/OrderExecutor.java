/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.trading.service.order;

import com.fluxtion.server.plugin.trading.service.common.Direction;
import com.fluxtion.server.plugin.trading.service.common.ExpiryTimeType;
import com.fluxtion.server.plugin.trading.service.common.OrderType;

import java.time.LocalDateTime;
import java.util.Set;

public interface OrderExecutor extends OrderFeed {

    default Order createOrder(String venue, String book, Direction direction, String symbol, double quantity, double price) {
        throw new UnsupportedOperationException("createOrder not supported");
    }

    default Order createOrder(
            String venue,
            String book,
            Direction direction,
            String symbol,
            double quantity,
            double price,
            OrderType orderType,
            ExpiryTimeType expiryTimeType,
            LocalDateTime expiryTime) {
        return createOrder(venue, book, direction, symbol, quantity, price, orderType, expiryTimeType, expiryTime, -1);
    }

    default Order createOrder(
            String venue,
            String book,
            Direction direction,
            String symbol,
            double quantity,
            double price,
            OrderType orderType,
            ExpiryTimeType expiryTimeType,
            LocalDateTime expiryTime,
            long clOrdId) {
        return createOrder(venue, null, book, direction, symbol, quantity, price, orderType, expiryTimeType, expiryTime, -1);
    }

    default Order createOrder(
            String venue,
            String account,
            String book,
            Direction direction,
            String symbol,
            double quantity,
            double price,
            OrderType orderType,
            ExpiryTimeType expiryTimeType,
            LocalDateTime expiryTime,
            long clOrdId) {
        throw new UnsupportedOperationException("createOrder not supported");
    }

    default Order createOrder(NewOrderRequest request) {
        throw new UnsupportedOperationException("createOrder not supported");
    }

    default void modifyOrder(long orderId, double price, double quantity) {
        modifyOrder(orderId, price, quantity, -1);
    }

    default void modifyOrder(long orderId, double price, double quantity, long nextOrderId) {
        throw new UnsupportedOperationException("modifyOrder not supported");
    }

    default long cancelOrder(long orderId) {
        return cancelOrder(orderId, -1);
    }

    default long cancelOrder(long orderId, long nextOrderId) {
        throw new UnsupportedOperationException("cancelOrder not supported");
    }

    default Set<String> venues() {
        return Set.of(getFeedName());
    }

    default boolean isVenueRegistered(String venueName) {
        return venues().contains(venueName);
    }
}
