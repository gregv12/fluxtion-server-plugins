package com.fluxtion.server.plugin.trading.component.marketdatafeed;

import com.fluxtion.runtime.StaticEventProcessor;
import com.fluxtion.server.dispatch.AbstractEventToInvocationStrategy;
import com.fluxtion.server.plugin.trading.service.marketdata.*;

public class MarketListenerInvocationStrategy extends AbstractEventToInvocationStrategy {

    @Override
    protected void dispatchEvent(Object event, StaticEventProcessor eventProcessor) {
        MarketDataListener marketDataListener = (MarketDataListener) eventProcessor;
        MarketFeedEvent marketFeedEvent = (MarketFeedEvent) event;
        switch (marketFeedEvent) {
            case MarketConnected marketConnected -> marketDataListener.marketDataVenueConnected(marketConnected);
            case MarketDisconnected marketDisconnected ->
                    marketDataListener.marketDataVenueDisconnected(marketDisconnected);
            case MarketDataBook marketDataBook -> marketDataListener.onMarketData(marketDataBook);
        }
    }

    @Override
    protected boolean isValidTarget(StaticEventProcessor eventProcessor) {
        return eventProcessor.exportsService(MarketDataListener.class);
    }
}
