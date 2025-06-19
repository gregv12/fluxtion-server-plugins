package com.fluxtion.server.plugin.trading.service.order;

import com.fluxtion.server.plugin.trading.service.common.Direction;

public interface Order {
    long clOrdId();

    double quantity();

    double price();

    Direction direction();

    OrderStatus orderStatus();

    double leavesQuantity();

    double filledQuantity();

    double cancelledQuantity();

    String symbol();

    String venue();

    String bookName();
}

