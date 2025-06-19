package com.fluxtion.server.plugin.trading.service.order;

public record OrderUpdateEvent(Order order) implements OrderEvent {
}
