package com.fluxtion.server.plugin.trading.service.common;

import lombok.Getter;

@Getter
public enum OrderType {
    MARKET('1'),
    LIMIT('2'),
    RESTING_LIMIT('R');

    private final char identifier;

    OrderType(char identifier) {
        this.identifier = identifier;
    }
}
