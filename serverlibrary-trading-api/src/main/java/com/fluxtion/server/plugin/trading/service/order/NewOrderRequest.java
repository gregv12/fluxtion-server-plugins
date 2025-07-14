/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.trading.service.order;

import com.fluxtion.server.plugin.trading.service.common.Direction;
import com.fluxtion.server.plugin.trading.service.common.ExpiryTimeType;
import com.fluxtion.server.plugin.trading.service.common.OrderType;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.Collection;

@Data
@Accessors(chain = true, fluent = true)
public class NewOrderRequest {
    private String venue;
    private String account;
    private String book;
    private Direction direction;
    private String symbol;
    private double quantity;
    private double price;
    private OrderType orderType;
    private ExpiryTimeType expiryTimeTyp;
    private LocalDateTime expiryTime;
    private Collection<Object> additionalParameters = new java.util.ArrayList<>();

    public NewOrderRequest addAdditionalParameter(Object parameter) {
        additionalParameters.add(parameter);
        return this;
    }

    public NewOrderRequest addAdditionalParameters(Collection<Object> parameters) {
        additionalParameters.addAll(parameters);
        return this;
    }

//    public static void main(String[] args) {
//        NewOrderRequest request = new NewOrderRequest()
//                .venue(" venue")
//                .account("account")
//                .book("book")
//                .direction(Direction.BUY)
//                .symbol("symbol")
//                .quantity(100)
//                .price(100)
//                .orderType(OrderType.MARKET)
//                .addAdditionalParameter(new Date());
//    }
}
