package com.fluxtion.server.plugin.trading.service.order;

public record OrderRejectEvent(Order order) implements OrderEvent {
}
