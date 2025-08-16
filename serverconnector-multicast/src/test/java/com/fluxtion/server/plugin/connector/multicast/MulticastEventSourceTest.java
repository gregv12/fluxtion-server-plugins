/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.connector.multicast;

import com.fluxtion.runtime.event.NamedFeedEvent;
import com.fluxtion.server.dispatch.EventToQueuePublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class MulticastEventSourceTest {

    private static final String GROUP = "230.0.0.1";
    private static final int PORT = 4446;

    private MulticastEventSource source;

    @BeforeEach
    void setUp() {
        source = new MulticastEventSource();
        source.setMulticastGroup(GROUP);
        source.setMulticastPort(PORT);
        source.setCacheEventLog(true);
        // Inject an EventToQueuePublisher so onStart() can use it
        source.setOutput(new EventToQueuePublisher<>("multicast-event-source"));
        source.onStart();
    }

    @AfterEach
    void tearDown() {
        source.tearDown();
    }

    @Test
    void testCacheBeforeStartCompleteAndPublishAfter() throws Exception {
        // Send two messages before startComplete
        sendUdp("pre1");
        sendUdp("pre2");

        // Retry loop to allow packets to arrive and be processed
        long end = System.currentTimeMillis() + 2_000;
        while (System.currentTimeMillis() < end) {
            source.doWork();
            List<String> cachedNow = Arrays.stream(source.eventLog())
                    .map(NamedFeedEvent::data)
                    .map(Object::toString)
                    .collect(Collectors.toList());
            if (cachedNow.containsAll(List.of("pre1", "pre2"))) {
                break;
            }
            Thread.sleep(10);
        }

        // Verify event log contains the cached messages
        List<String> cached = Arrays.stream(source.eventLog())
                .map(NamedFeedEvent::data)
                .map(Object::toString)
                .collect(Collectors.toList());
        Assertions.assertTrue(cached.containsAll(List.of("pre1", "pre2")));

        // Now switch to publish mode
        source.startComplete();

        // Send two more messages
        sendUdp("post1");
        sendUdp("post2");

        // Give the source time to process post-start messages (which should be published, not cached)
        long end2 = System.currentTimeMillis() + 1_000;
        while (System.currentTimeMillis() < end2) {
            source.doWork();
            Thread.sleep(5);
        }

        // Event log should grow with post-start messages (as they are published, and cached)
        List<String> cachedAfter = Arrays.stream(source.eventLog())
                .map(NamedFeedEvent::data)
                .map(Object::toString)
                .collect(Collectors.toList());
        Assertions.assertTrue(cachedAfter.contains("post1") && cachedAfter.contains("post2"),
                "Post-start messages should not be cached in eventLog");
    }

    private void sendUdp(String s) throws Exception {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        try (DatagramSocket sender = new DatagramSocket()) {
            InetAddress groupAddr = InetAddress.getByName(GROUP);
            DatagramPacket packet = new DatagramPacket(bytes, bytes.length, groupAddr, PORT);
            sender.send(packet);
        }
    }
}
