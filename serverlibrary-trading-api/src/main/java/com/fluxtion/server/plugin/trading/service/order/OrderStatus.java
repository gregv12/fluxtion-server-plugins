package com.fluxtion.server.plugin.trading.service.order;

public enum OrderStatus {

    CREATED,
    PENDING_NEW,
    NEW,
    PARTIAL_FILLED,
    FILLED,
    CANCELLED,
    DONE_FOR_DAY,
    REJECTED,
}
