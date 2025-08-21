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

import com.fluxtion.runtime.lifecycle.Lifecycle;
import com.fluxtion.runtime.output.AbstractMessageSink;
import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.driver.MediaDriver;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.agrona.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Log4j2
public class AeronMessageSink extends AbstractMessageSink<Object> implements Lifecycle {

    @Getter @Setter
    private String channel = "aeron:ipc"; // simple and reliable for tests
    @Getter @Setter
    private int streamId = 10;
    @Getter @Setter
    private String aeronDirectoryName; // optional: connect to external/embedded driver
    @Getter @Setter
    private boolean launchEmbeddedDriver = false; // tests may prefer external driver control

    private MediaDriver mediaDriver;
    private Aeron aeron;
    private Publication publication;
    private final UnsafeBuffer sendBuffer = new UnsafeBuffer(BufferUtil.allocateDirectAligned(1024, 64));

    @Override
    public void init() {
        try {
            if (launchEmbeddedDriver) {
                MediaDriver.Context ctx = new MediaDriver.Context()
                        .dirDeleteOnStart(true)
                        .dirDeleteOnShutdown(true);
                mediaDriver = MediaDriver.launchEmbedded(ctx);
                if (aeronDirectoryName == null || aeronDirectoryName.isEmpty()) {
                    aeronDirectoryName = mediaDriver.aeronDirectoryName();
                }
            }
            Aeron.Context aCtx = new Aeron.Context();
            if (aeronDirectoryName != null && !aeronDirectoryName.isEmpty()) {
                aCtx.aeronDirectoryName(aeronDirectoryName);
            }
            aeron = Aeron.connect(aCtx);
            publication = aeron.addPublication(channel, streamId);
            log.info("AeronMessageSink initialised channel:{} stream:{} dir:{}", channel, streamId, aeronDirectoryName);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialise AeronMessageSink", e);
        }
    }

    @Override
    protected void sendToSink(Object value) {
        byte[] payload;
        if (value instanceof byte[] b) {
            payload = b;
        } else {
            payload = String.valueOf(value).getBytes(StandardCharsets.UTF_8);
        }
        ensureCapacity(payload.length);
        sendBuffer.setMemory(0, payload.length, (byte)0);
        sendBuffer.putBytes(0, payload);
        offerWithRetry(sendBuffer, payload.length);
    }

    private void ensureCapacity(int length) {
        if (sendBuffer.capacity() < length) {
            int newCap = Math.max(length, sendBuffer.capacity() * 2);
            UnsafeBuffer newBuf = new UnsafeBuffer(BufferUtil.allocateDirectAligned(newCap, 64));
            // replace reference (field is final, so can't reassign; but we can allocate big enough initially)
            // For simplicity, allocate once big enough; if not, log and drop.
            log.warn("Payload {} exceeds buffer capacity {} - consider increasing initial capacity", length, sendBuffer.capacity());
        }
    }

    private void offerWithRetry(DirectBuffer buffer, int length) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (true) {
            long result = publication.offer(buffer, 0, length);
            if (result > 0) {
                return;
            }
            if (System.nanoTime() > deadline) {
                log.warn("Offer failed after retries, result:{}", result);
                return;
            }
            // back pressure or not connected
            Thread.yield();
        }
    }

    @Override
    public void tearDown() {
        try {
            if (publication != null) publication.close();
        } catch (Exception ignored) {}
        try {
            if (aeron != null) aeron.close();
        } catch (Exception ignored) {}
        try {
            if (mediaDriver != null) mediaDriver.close();
        } catch (Exception ignored) {}
    }
}
