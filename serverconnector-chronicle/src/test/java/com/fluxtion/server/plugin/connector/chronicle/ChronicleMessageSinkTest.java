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

import net.openhft.chronicle.bytes.MethodReader;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

class ChronicleMessageSinkTest {

    private static final boolean TEST_KEEP_FILES = Boolean.getBoolean("TEST_KEEP_FILES");

    @TempDir
    Path tempDir;

    private ChronicleQueue readerQueue;

    // Testable subclass to expose protected sendToSink for unit testing
    static class TestableChronicleMessageSink extends ChronicleMessageSink {
        public void write(Object value) {
            super.sendToSink(value);
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        if (readerQueue != null) {
            readerQueue.close();
            readerQueue = null;
        }
        if (!TEST_KEEP_FILES && tempDir != null) {
            // Best-effort cleanup of temp directory
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
    void writesAndReadsMessagesFromChronicleQueue() {
        // Arrange
        Path chroniclePath = tempDir;
        TestableChronicleMessageSink sink = new TestableChronicleMessageSink();
        sink.setChroniclePath(chroniclePath.toString());

        // Act: init sink and write messages
        sink.init();
        sink.write("hello");
        sink.write("world");
        sink.tearDown(); // ensures queue/appender closed

        // Read back via Chronicle reader on the same path
        List<String> received = new ArrayList<>();
        readerQueue = ChronicleQueue.single(chroniclePath.resolve("chronicle-queue").toString());
        ExcerptTailer tailer = readerQueue.createTailer();
        MethodReader reader = tailer.methodReader((MessageSink) v -> received.add(String.valueOf(v)));

        while (reader.readOne()) {
            // Loop until no more messages at this moment
        }

        // Assert
        Assertions.assertEquals(List.of("hello", "world"), received);
    }
}
