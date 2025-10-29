package com.fluxtion.server.plugin.trading.component.marketdatafeed;

import com.fluxtion.runtime.StaticEventProcessor;
import com.fluxtion.server.dispatch.AbstractEventToInvocationStrategy;
import com.fluxtion.server.plugin.trading.service.marketdata.*;

/**
 * Strategy for dispatching market data events to appropriate MarketDataListener implementations.
 * This class handles the routing of different types of market feed events to their corresponding
 * listener methods based on the event type.
 */
public class MarketListenerInvocationStrategy extends AbstractEventToInvocationStrategy {

    /**
     * Dispatches market feed events to the appropriate handler method in the MarketDataListener.
     * Uses pattern matching to determine the event type and routes to the corresponding callback.
     *
     * @param event          the market feed event to dispatch
     * @param eventProcessor the event processor implementing MarketDataListener interface
     */
    @Override
    protected void dispatchEvent(Object event, StaticEventProcessor eventProcessor) {
        MarketDataListener marketDataListener = (MarketDataListener) eventProcessor;
        MarketFeedEvent marketFeedEvent = (MarketFeedEvent) event;
        switch (marketFeedEvent) {
            case MarketConnected marketConnected -> marketDataListener.marketDataVenueConnected(marketConnected);
            case MarketDisconnected marketDisconnected ->
                    marketDataListener.marketDataVenueDisconnected(marketDisconnected);
            case MarketDataBook marketDataBook -> marketDataListener.onMarketData(marketDataBook);
            case MultilevelMarketDataBook multilevelBook ->
                    marketDataListener.onMultilevelMarketData(multilevelBook);
        }
    }

    /**
     * Verifies if the event processor is a valid target for market data events.
     * Checks if the processor implements the MarketDataListener interface.
     *
     * @param eventProcessor the event processor to validate
     * @return true if the processor implements MarketDataListener, false otherwise
     */
    @Override
    protected boolean isValidTarget(StaticEventProcessor eventProcessor) {
        return eventProcessor.exportsService(MarketDataListener.class);
    }
}
