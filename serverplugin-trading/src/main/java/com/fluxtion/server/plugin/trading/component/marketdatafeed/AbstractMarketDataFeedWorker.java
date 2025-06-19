package com.fluxtion.server.plugin.trading.component.marketdatafeed;

import com.fluxtion.agrona.concurrent.Agent;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@RequiredArgsConstructor
public abstract class AbstractMarketDataFeedWorker extends AbstractMarketDataFeed implements Agent {

    private final String roleName;

    @Override
    public String roleName() {
        return roleName;
    }
}
