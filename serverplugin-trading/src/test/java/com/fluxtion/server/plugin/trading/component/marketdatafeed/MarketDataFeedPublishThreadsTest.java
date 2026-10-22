package com.fluxtion.server.plugin.trading.component.marketdatafeed;

import com.fluxtion.agrona.concurrent.OneToOneConcurrentArrayQueue;
import com.fluxtion.server.plugin.trading.component.TwoThreadPublish;
import com.fluxtion.server.plugin.trading.service.marketdata.MarketConnected;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A feed's I/O thread and its connect/admin paths can both publish: none may be lost. */
class MarketDataFeedPublishThreadsTest {

    @Test
    void everyMarketFeedEventPublishedFromTwoThreadsReachesTheSubscriber() throws Exception {
        AbstractMarketDataFeed feed = new AbstractMarketDataFeed() {
            @Override
            protected void subscribeToSymbol(String feedName, String venueName, String symbol) {
            }
        };
        OneToOneConcurrentArrayQueue<Object> queue = TwoThreadPublish.attachQueue(feed, AbstractMarketDataFeed.class);
        MarketConnected event = new MarketConnected("feed");

        int received = TwoThreadPublish.publishFromTwoThreads(queue, () -> feed.publish(event));

        assertEquals(2 * TwoThreadPublish.PER_THREAD, received, "market feed events lost to concurrent publishes");
    }
}
