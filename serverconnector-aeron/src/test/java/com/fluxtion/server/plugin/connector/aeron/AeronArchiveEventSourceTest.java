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
package com.fluxtion.server.plugin.connector.aeron;

import com.fluxtion.runtime.event.NamedFeedEvent;
import com.fluxtion.server.dispatch.EventToQueuePublisher;
import io.aeron.driver.MediaDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class AeronArchiveEventSourceTest {

    private MediaDriver mediaDriver;

    @BeforeEach
    void setUp() {
        mediaDriver = MediaDriver.launchEmbedded(new MediaDriver.Context()
                .dirDeleteOnStart(true)
                .dirDeleteOnShutdown(true));
    }

    @AfterEach
    void tearDown() {
        try {
            if (mediaDriver != null) mediaDriver.close();
        } catch (Exception ignored) {
        }
    }

    @Test
    void testLiveModeCacheAndPublish() throws Exception {
        AeronArchiveEventSource source = new AeronArchiveEventSource();
        source.setMode(AeronArchiveEventSource.Mode.LIVE);
        source.setChannel("aeron:ipc");
        source.setStreamId(10);
        source.setAeronDirectoryName(mediaDriver.aeronDirectoryName());
        source.setLaunchEmbeddedDriver(false);
        source.setCacheEventLog(true);
        source.setOutput(new EventToQueuePublisher<>("aeron-live"));
        source.onStart();

        AeronMessageSink sink = new AeronMessageSink();
        sink.setChannel("aeron:ipc");
        sink.setStreamId(10);
        sink.setAeronDirectoryName(mediaDriver.aeronDirectoryName());
        sink.setLaunchEmbeddedDriver(false);
        sink.init();

        sink.sendToSink("pre1");
        sink.sendToSink("pre2");

        long end = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < end) {
            source.doWork();
            List<String> cachedNow = Arrays.stream(source.eventLog())
                    .map(NamedFeedEvent::data)
                    .map(Object::toString)
                    .collect(Collectors.toList());
            if (cachedNow.containsAll(List.of("pre1", "pre2"))) {
                break;
            }
            Thread.sleep(5);
        }

        List<String> cached = Arrays.stream(source.eventLog())
                .map(NamedFeedEvent::data)
                .map(Object::toString)
                .collect(Collectors.toList());
        Assertions.assertTrue(cached.containsAll(List.of("pre1", "pre2")));

        source.startComplete();
        sink.sendToSink("post1");
        sink.sendToSink("post2");

        long end2 = System.currentTimeMillis() + 1000;
        while (System.currentTimeMillis() < end2) {
            source.doWork();
            Thread.sleep(5);
        }

        List<String> cachedAfter = Arrays.stream(source.eventLog())
                .map(NamedFeedEvent::data)
                .map(Object::toString)
                .collect(Collectors.toList());
        Assertions.assertTrue(cachedAfter.contains("post1") && cachedAfter.contains("post2"));

        sink.tearDown();
        source.tearDown();
    }
}
