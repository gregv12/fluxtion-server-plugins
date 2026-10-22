package com.fluxtion.server.plugin.trading.component.quoterequestfeed;

import com.fluxtion.agrona.concurrent.OneToOneConcurrentArrayQueue;
import com.fluxtion.server.plugin.trading.component.TwoThreadPublish;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The poll thread and an async fetch completion can both publish: none may be lost. */
class QuoteRequestFeedPublishThreadsTest {

    @Test
    void everyQuoteRequestEventPublishedFromTwoThreadsReachesTheSubscriber() throws Exception {
        AbstractQuoteRequestFeed feed = new AbstractQuoteRequestFeed() {
            @Override
            protected void requestLiveQuotes() {
            }
        };
        OneToOneConcurrentArrayQueue<Object> queue = TwoThreadPublish.attachQueue(feed, AbstractQuoteRequestFeed.class);

        int received = TwoThreadPublish.publishFromTwoThreads(queue, feed::publishNoData);

        assertEquals(2 * TwoThreadPublish.PER_THREAD, received, "quote request events lost to concurrent publishes");
    }
}
