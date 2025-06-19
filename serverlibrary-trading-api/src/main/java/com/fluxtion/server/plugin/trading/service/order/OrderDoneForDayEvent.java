package com.fluxtion.server.plugin.trading.service.order;

public record OrderDoneForDayEvent(Order order) implements OrderEvent {
}
