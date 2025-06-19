package com.fluxtion.server.plugin.trading.service.order;

public record OrderCancelRejectEvent(Order order) implements OrderEvent {
}
