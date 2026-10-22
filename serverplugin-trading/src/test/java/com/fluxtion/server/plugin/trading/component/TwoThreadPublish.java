package com.fluxtion.server.plugin.trading.component;

import com.fluxtion.agrona.concurrent.OneToOneConcurrentArrayQueue;
import com.fluxtion.server.dispatch.EventToQueuePublisher;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Publishes from two threads into a source's single-producer queue while a subscriber drains it, and counts what
 * arrives: an unserialised publish from two threads can write the same slot and lose an event.
 */
public final class TwoThreadPublish {

    public static final int PER_THREAD = 200_000;

    private TwoThreadPublish() {
    }

    /** Gives {@code source} a publisher (its {@code targetQueue}, declared by {@code declaringClass}) into a fresh queue. */
    public static OneToOneConcurrentArrayQueue<Object> attachQueue(Object source, Class<?> declaringClass) throws Exception {
        OneToOneConcurrentArrayQueue<Object> queue = new OneToOneConcurrentArrayQueue<>(1 << 16);
        EventToQueuePublisher<Object> publisher = new EventToQueuePublisher<>("source");
        publisher.addTargetQueue(queue, "subscriber");
        Field target = declaringClass.getDeclaredField("targetQueue");
        target.setAccessible(true);
        target.set(source, publisher);
        return queue;
    }

    /** Runs {@code publishOne} PER_THREAD times on each of two threads; returns how many events the subscriber got. */
    public static int publishFromTwoThreads(OneToOneConcurrentArrayQueue<Object> queue, Runnable publishOne) throws Exception {
        AtomicInteger received = new AtomicInteger();
        AtomicBoolean publishing = new AtomicBoolean(true);
        Thread subscriber = new Thread(() -> {
            while (publishing.get() || !queue.isEmpty()) {
                received.addAndGet(queue.drain(e -> { }));
            }
        }, "subscriber-agent");
        CountDownLatch start = new CountDownLatch(1);
        Runnable publish = () -> {
            try {
                start.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            for (int i = 0; i < PER_THREAD; i++) {
                publishOne.run();
            }
        };
        Thread io = new Thread(publish, "io-thread");
        Thread strategy = new Thread(publish, "strategy-thread");
        subscriber.start();
        io.start();
        strategy.start();
        start.countDown();
        io.join();
        strategy.join();
        publishing.set(false);
        subscriber.join(10_000);
        return received.get();
    }
}
