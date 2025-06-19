package com.fluxtion.server.plugin.trading.service.order.impl;

import com.fluxtion.server.plugin.trading.service.common.Direction;
import com.fluxtion.server.plugin.trading.service.common.ExpiryTimeType;
import com.fluxtion.server.plugin.trading.service.common.OrderType;
import com.fluxtion.server.plugin.trading.service.order.Order;
import com.fluxtion.server.plugin.trading.service.order.OrderStatus;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

@Getter
@Setter
@ToString
@Accessors(chain = true, fluent = true)
public class MutableOrder implements Order {

    private long clOrdId;
    private long currentClOrdId;
    private String venue;
    private String symbol;
    private String bookName;
    private OrderType orderType;
    private Direction direction;
    private ExpiryTimeType expiryTimeType;
    private double quantity;
    private double price;
    //exchange fields
    private String exchangeOrderId;
    //calculated fields
    private OrderStatus orderStatus = OrderStatus.CREATED;
    private double leavesQuantity;
    private double filledQuantity = 0;
    private double cancelledQuantity = 0;

    public MutableOrder copy() {
        return new MutableOrder()
                .clOrdId(clOrdId)
                .currentClOrdId(currentClOrdId)
                .venue(venue)
                .bookName(bookName)
                .symbol(symbol)
                .orderType(orderType)
                .direction(direction)
                .expiryTimeType(expiryTimeType)
                .quantity(quantity)
                .price(price)
                //exchange fields
                .exchangeOrderId(exchangeOrderId)
                //calculated fields
                .orderStatus(orderStatus)
                .leavesQuantity(leavesQuantity)
                .filledQuantity(filledQuantity)
                .cancelledQuantity(cancelledQuantity);
    }
}
