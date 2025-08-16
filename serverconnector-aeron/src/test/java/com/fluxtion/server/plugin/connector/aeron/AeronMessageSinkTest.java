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

import io.aeron.Aeron;
import io.aeron.FragmentAssembler;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import org.junit.jupiter.api.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class AeronMessageSinkTest {

    private MediaDriver mediaDriver;
    private Aeron aeron;

    private final String channel = "aeron:ipc";
    private final int streamId = 10;

    @BeforeEach
    void setUp() {
        mediaDriver = MediaDriver.launchEmbedded(new MediaDriver.Context()
                .dirDeleteOnStart(true)
                .dirDeleteOnShutdown(true));
        aeron = Aeron.connect(new Aeron.Context().aeronDirectoryName(mediaDriver.aeronDirectoryName()));
    }

    @AfterEach
    void tearDown() {
        try { if (aeron != null) aeron.close(); } catch (Exception ignored) {}
        try { if (mediaDriver != null) mediaDriver.close(); } catch (Exception ignored) {}
    }

    @Test
    void testSendStringAndBytes() throws Exception {
        AeronMessageSink sink = new AeronMessageSink();
        sink.setChannel(channel);
        sink.setStreamId(streamId);
        sink.setAeronDirectoryName(mediaDriver.aeronDirectoryName());
        sink.setLaunchEmbeddedDriver(false);
        sink.init();

        List<byte[]> received = new ArrayList<>();
        Subscription sub = aeron.addSubscription(channel, streamId);
        FragmentAssembler handler = new FragmentAssembler((buffer, offset, length, header) -> {
            byte[] data = new byte[length];
            for (int i = 0; i < length; i++) {
                data[i] = buffer.getByte(offset + i);
            }
            received.add(data);
        });

        String s = "hello-aeron";
        sink.sendToSink(s);
        awaitReceive(sub, handler, received, 1, 2000);
        Assertions.assertEquals(s, new String(received.get(0), StandardCharsets.UTF_8));

        byte[] payload = new byte[]{1,2,3,4,5};
        sink.sendToSink(payload);
        awaitReceive(sub, handler, received, 2, 2000);
        Assertions.assertArrayEquals(payload, received.get(1));

        sink.tearDown();
        sub.close();
    }

    private void awaitReceive(Subscription sub, FragmentAssembler handler, List<byte[]> list, int expected, long timeoutMs) throws InterruptedException {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            sub.poll(handler, 10);
            if (list.size() >= expected) return;
            Thread.sleep(5);
        }
        Assertions.fail("Timed out waiting for aeron messages: expected=" + expected + " got=" + list.size());
    }
}
