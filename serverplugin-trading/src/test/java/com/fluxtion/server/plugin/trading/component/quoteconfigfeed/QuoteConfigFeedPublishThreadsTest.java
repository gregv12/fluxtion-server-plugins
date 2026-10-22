package com.fluxtion.server.plugin.trading.component.quoteconfigfeed;

import com.fluxtion.agrona.concurrent.OneToOneConcurrentArrayQueue;
import com.fluxtion.server.plugin.trading.component.TwoThreadPublish;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The feed's poll thread and admin both publish config: none may be lost. */
class QuoteConfigFeedPublishThreadsTest {

    @Test
    void everyConfigEventPublishedFromTwoThreadsReachesTheSubscriber() throws Exception {
        AbstractQuoteConfigFeed feed = new AbstractQuoteConfigFeed() {
            @Override
            protected void requestConfig() {
            }
        };
        OneToOneConcurrentArrayQueue<Object> queue = TwoThreadPublish.attachQueue(feed, AbstractQuoteConfigFeed.class);

        int received = TwoThreadPublish.publishFromTwoThreads(queue, () -> feed.publishBucketConfig(100192L, "USD-MXN", 1.5));

        assertEquals(2 * TwoThreadPublish.PER_THREAD, received, "config events lost to concurrent publishes");
    }
}
