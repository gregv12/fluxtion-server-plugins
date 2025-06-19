package com.fluxtion.server.plugin.trading.service.order;

public sealed interface OrderEvent
        permits
        OrderVenueConnectedEvent,
        OrderVenueDisconnectedEvent,
        OrderRejectEvent,
        OrderCancelRejectEvent,
        OrderUpdateEvent,
        OrderFillEvent,
        OrderFilledEvent,
        OrderCancelEvent,
        OrderDoneForDayEvent {
}
