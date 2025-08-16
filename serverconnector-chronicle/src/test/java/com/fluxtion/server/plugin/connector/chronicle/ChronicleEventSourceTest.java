/*
 * Copyright (c) 2025 gregory higgins.
 * All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Server Side Public License, version 1,
 * as published by MongoDB, Inc.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Server Side Public License for more details.
 *
 * You should have received a copy of the Server Side Public License
 * along with this program.  If not, see
 * <http://www.mongodb.com/licensing/server-side-public-license>.
 */
package com.fluxtion.server.plugin.connector.chronicle;

import com.fluxtion.agrona.concurrent.OneToOneConcurrentArrayQueue;
import com.fluxtion.runtime.event.NamedFeedEvent;
import com.fluxtion.server.dispatch.EventToQueuePublisher;
import net.openhft.chronicle.queue.ChronicleQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

class ChronicleEventSourceTest {

    private static final boolean TEST_KEEP_FILES = Boolean.getBoolean("TEST_KEEP_FILES");

    @TempDir
    Path tempDir;

    private ChronicleQueue writerQueue; // closed in @AfterEach

    // Simple local interface mirroring the Chronicle method name used in sink/source
    interface MessageSink {
        void onEvent(Object value);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (writerQueue != null) {
            writerQueue.close();
            writerQueue = null;
        }
        if (!TEST_KEEP_FILES && tempDir != null) {
            try (var walk = Files.walk(tempDir)) {
                walk.sorted((a, b) -> b.compareTo(a)) // delete children first
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (Exception ignored) {
                            }
                        });
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    void cachePreStart_thenDispatchOnStartComplete_thenPublishNew() throws Exception {
        Path chroniclePath = tempDir;

        // Seed queue with pre-start messages
        writerQueue = ChronicleQueue.single(chroniclePath.resolve("chronicle-queue").toString());
        var appender = writerQueue.createAppender().methodWriter(MessageSink.class);
        appender.onEvent("pre1");
        appender.onEvent("pre2");

        // Set up target queue + publisher
        EventToQueuePublisher<String> eventToQueue = new EventToQueuePublisher<>("chronicle-event-source");
        OneToOneConcurrentArrayQueue<Object> targetQueue = new OneToOneConcurrentArrayQueue<>(128);
        eventToQueue.addTargetQueue(targetQueue, "outputQueue");

        // Configure and start source in cache mode
        ChronicleEventSource source = new ChronicleEventSource();
        source.setChroniclePath(chroniclePath.toString());
        source.setCacheEventLog(true); // cache pre-start
        source.setOutput(eventToQueue);

        source.onStart();        // initializes reader and reads cached items (not published yet)
        source.startComplete();  // switch to publish mode and dispatch cached

        // Drain published cached events
        ArrayList<Object> drained = new ArrayList<>();
        targetQueue.drainTo(drained, 100);
        Assertions.assertEquals(List.of("pre1", "pre2"),
                drained.stream().map(String::valueOf).collect(Collectors.toList()));

        // Append new post-start events
        appender.onEvent("post1");
        appender.onEvent("post2");

        // Process new events
        source.doWork();
        drained.clear();
        targetQueue.drainTo(drained, 100);
        Assertions.assertEquals(List.of("post1", "post2"),
                drained.stream().map(String::valueOf).collect(Collectors.toList()));

        // Verify event log contains at least the pre-start cached events (may also include post events if caching remains enabled)
        List<String> logged = eventToQueue.getEventLog().stream()
                .map(NamedFeedEvent::data).map(String::valueOf).collect(Collectors.toList());
        Assertions.assertTrue(logged.containsAll(List.of("pre1", "pre2")));
    }

    @Test
    void latestReadStrategy_skipsExisting_andReadsOnlyNew() throws Exception {
        Path chroniclePath = tempDir;

        // Seed queue with old data that should be skipped in LATEST
        writerQueue = ChronicleQueue.single(chroniclePath.resolve("chronicle-queue").toString());
        var appender = writerQueue.createAppender().methodWriter(MessageSink.class);
        appender.onEvent("old1");
        appender.onEvent("old2");

        // Set up target queue + publisher
        EventToQueuePublisher<String> eventToQueue = new EventToQueuePublisher<>("chronicle-event-source");
        OneToOneConcurrentArrayQueue<Object> targetQueue = new OneToOneConcurrentArrayQueue<>(128);
        eventToQueue.addTargetQueue(targetQueue, "outputQueue");

        // Configure source with LATEST
        ChronicleEventSource source = new ChronicleEventSource();
        source.setChroniclePath(chroniclePath.toString());
        source.setReadStrategy(ReadStrategy.LATEST);
        source.setCacheEventLog(false);
        source.setOutput(eventToQueue);

        source.onStart();        // should position to end

        // Append new events only after onStart
        appender.onEvent("new1");
        appender.onEvent("new2");

        // Process and verify only new events are delivered
        source.startComplete();
        source.doWork();

        ArrayList<Object> drained = new ArrayList<>();
        targetQueue.drainTo(drained, 100);
        Assertions.assertEquals(List.of("new1", "new2"),
                drained.stream().map(String::valueOf).collect(Collectors.toList()));
    }
}
