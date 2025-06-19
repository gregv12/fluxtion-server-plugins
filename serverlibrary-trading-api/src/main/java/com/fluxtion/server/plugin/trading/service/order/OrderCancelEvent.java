package com.fluxtion.server.plugin.trading.service.order;

public record OrderCancelEvent(Order order) implements OrderEvent {
}
