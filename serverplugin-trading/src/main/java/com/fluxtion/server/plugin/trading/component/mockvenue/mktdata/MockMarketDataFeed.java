package com.fluxtion.server.plugin.trading.component.mockvenue.mktdata;

import com.fluxtion.server.plugin.trading.component.marketdatafeed.AbstractMarketDataFeed;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class MockMarketDataFeed extends AbstractMarketDataFeed {

    @Override
    protected void subscribeToSymbol(String feedName, String venueName, String symbol) {
        log.info("service:{} subscribeToSymbol: {} venue:{} feed:{}", getServiceName(), symbol, venueName, feedName);
    }

    @Override
    public String feedName() {
        return "mockMarketDataFeed";
    }
}
