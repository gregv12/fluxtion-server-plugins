/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.connector.file;

import com.fluxtion.agrona.concurrent.OneToOneConcurrentArrayQueue;
import com.fluxtion.server.dispatch.EventToQueuePublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class FileEventSourceTest {
    @TempDir
    Path tempDir;

    @Test
    public void testReadEvents() throws IOException {

        // Create a temporary file.
        // This is guaranteed to be deleted after the test finishes.
        final Path tempFile = Files.createFile(tempDir.resolve("myfile.txt"));
        System.out.println(tempDir.toAbsolutePath());
        // Write something to it.
        Files.writeString(tempFile, """
                        Hello World
                        Test 1
                        """,
                StandardOpenOption.SYNC);

        FileEventSource fileEventSource = new FileEventSource();
        fileEventSource.setFilename(tempFile.toFile().getAbsolutePath());
        fileEventSource.setCacheEventLog(true);

        EventToQueuePublisher<String> eventToQueue = new EventToQueuePublisher<>("myQueue");
        OneToOneConcurrentArrayQueue<String> targetQueue = new OneToOneConcurrentArrayQueue<>(100);
        eventToQueue.addTargetQueue(targetQueue, "outputQueue");
        eventToQueue.setCacheEventLog(true);
        fileEventSource.setOutput(eventToQueue);

        fileEventSource.onStart();
        fileEventSource.start();
        fileEventSource.startComplete();
        fileEventSource.doWork();

        targetQueue.drain(System.out::println);

//        // Read it.
//        final String s = Files.readString(tempFile);
//
//        // Check that what was written is correct.
//        Assertions.assertEquals("Hello World", s);
    }

}
