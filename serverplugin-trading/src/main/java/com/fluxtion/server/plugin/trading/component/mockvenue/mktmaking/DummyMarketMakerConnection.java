package com.fluxtion.server.plugin.trading.component.mockvenue.mktmaking;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class DummyMarketMakerConnection {

    public void publish(Object object) {
        log.info("Published object {}", object);
    }
}
