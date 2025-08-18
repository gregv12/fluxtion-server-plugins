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
package com.fluxtion.server.plugin.connector.multicast;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

public class MulticastMessageSinkTest {

    private static final String GROUP = "230.0.0.1";
    private static final int PORT = 4446;

    private MulticastSocket listener;
    private InetAddress groupAddr;

    @BeforeEach
    void setUp() throws Exception {
        Assumptions.assumeTrue(MulticastTestSupport.canSendAndReceive(GROUP, PORT), "Multicast not working in this environment; skipping test");
        groupAddr = InetAddress.getByName(GROUP);
        listener = new MulticastSocket(PORT);
        listener.setReuseAddress(true);
        listener.setSoTimeout(250);
        listener.joinGroup(groupAddr);
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            if (listener != null) {
                try { listener.leaveGroup(groupAddr); } catch (Exception ignored) {}
                listener.close();
            }
        } catch (Exception ignored) {}
    }

    @Test
    void testSendStringAndBytes() throws Exception {
        MulticastMessageSink sink = new MulticastMessageSink();
        sink.setMulticastGroup(GROUP);
        sink.setMulticastPort(PORT);
        sink.init();

        // send string
        String msg = "hello-mcast";
        sink.sendToSink(msg);
        String receivedStr = receiveStringWithRetry(2000);
        Assertions.assertEquals(msg, receivedStr);

        // send bytes
        byte[] payload = new byte[]{1, 2, 3, 4, 5};
        sink.sendToSink(payload);
        byte[] receivedBytes = receiveBytesWithRetry(2000);
        Assertions.assertArrayEquals(payload, receivedBytes);

        sink.tearDown();
    }

    private String receiveStringWithRetry(long timeoutMillis) throws Exception {
        long end = System.currentTimeMillis() + timeoutMillis;
        byte[] buf = new byte[65536];
        DatagramPacket p = new DatagramPacket(buf, buf.length);
        while (System.currentTimeMillis() < end) {
            try {
                listener.receive(p);
                return new String(p.getData(), p.getOffset(), p.getLength(), StandardCharsets.UTF_8);
            } catch (SocketTimeoutException ignored) {
                // retry
            }
        }
        Assertions.fail("Did not receive multicast packet in time");
        return null; // unreachable
    }

    private byte[] receiveBytesWithRetry(long timeoutMillis) throws Exception {
        long end = System.currentTimeMillis() + timeoutMillis;
        byte[] buf = new byte[65536];
        DatagramPacket p = new DatagramPacket(buf, buf.length);
        while (System.currentTimeMillis() < end) {
            try {
                listener.receive(p);
                byte[] out = new byte[p.getLength()];
                System.arraycopy(p.getData(), p.getOffset(), out, 0, p.getLength());
                return out;
            } catch (SocketTimeoutException ignored) {
                // retry
            }
        }
        Assertions.fail("Did not receive multicast packet in time");
        return null; // unreachable
    }
}
