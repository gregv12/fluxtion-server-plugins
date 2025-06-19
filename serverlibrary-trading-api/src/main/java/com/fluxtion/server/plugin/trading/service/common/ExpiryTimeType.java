package com.fluxtion.server.plugin.trading.service.common;

import lombok.Getter;

@Getter
public enum ExpiryTimeType {

    DAY('0'),
    GTC('1'),
    IOC('3'),
    FOK('4');

    private final char identifier;

    ExpiryTimeType(char identifier) {
        this.identifier = identifier;
    }
}
