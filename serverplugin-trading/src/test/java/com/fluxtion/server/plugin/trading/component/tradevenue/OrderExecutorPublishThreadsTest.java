package com.fluxtion.server.plugin.trading.component.tradevenue;

import com.fluxtion.agrona.concurrent.OneToOneConcurrentArrayQueue;
import com.fluxtion.server.plugin.trading.component.TwoThreadPublish;
import com.fluxtion.server.plugin.trading.component.mockvenue.orders.MockOrderExecutor;
import com.fluxtion.server.plugin.trading.service.order.OrderUpdateEvent;
import com.fluxtion.server.plugin.trading.service.order.impl.MutableOrder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The FIX thread and the strategy agents both publish order events: none may be lost. */
class OrderExecutorPublishThreadsTest {

    @Test
    void everyOrderEventPublishedFromTwoThreadsReachesTheSubscriber() throws Exception {
        MockOrderExecutor venue = new MockOrderExecutor();
        OneToOneConcurrentArrayQueue<Object> queue = TwoThreadPublish.attachQueue(venue, AbstractOrderExecutor.class);
        OrderUpdateEvent event = new OrderUpdateEvent(new MutableOrder());

        int received = TwoThreadPublish.publishFromTwoThreads(queue, () -> venue.publish(event));

        assertEquals(2 * TwoThreadPublish.PER_THREAD, received, "order events lost to concurrent publishes");
    }
}
