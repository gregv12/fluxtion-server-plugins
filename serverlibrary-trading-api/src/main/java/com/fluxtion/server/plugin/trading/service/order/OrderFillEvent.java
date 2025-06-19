package com.fluxtion.server.plugin.trading.service.order;

public record OrderFillEvent(Order order, double price, double quantity, String execId) implements OrderEvent {
}
