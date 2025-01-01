/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.connector.file;

import com.fluxtion.agrona.concurrent.OneToOneConcurrentArrayQueue;
import com.fluxtion.runtime.event.NamedFeedEvent;
import com.fluxtion.server.dispatch.EventToQueuePublisher;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class FileEventSourceTest {
    @TempDir
    Path tempDir;

    @Test
    public void testReadEvents() throws IOException {
        final Path tempFile = Files.createFile(tempDir.resolve("myfile.txt"));
        Files.writeString(tempFile, """
                        item 1
                        item 2
                        """,
                StandardOpenOption.SYNC);

        FileEventSource fileEventSource = new FileEventSource();
        fileEventSource.setFilename(tempFile.toFile().getAbsolutePath());
        fileEventSource.setCacheEventLog(true);

        EventToQueuePublisher<String> eventToQueue = new EventToQueuePublisher<>("myQueue");
        OneToOneConcurrentArrayQueue<String> targetQueue = new OneToOneConcurrentArrayQueue<>(100);
        eventToQueue.addTargetQueue(targetQueue, "outputQueue");
        fileEventSource.setOutput(eventToQueue);

        fileEventSource.onStart();
        fileEventSource.start();
        fileEventSource.startComplete();
        fileEventSource.doWork();

        ArrayList<String> actual = new ArrayList<>();

        targetQueue.drainTo(actual, 100);
        Assertions.assertIterableEquals(List.of("item 1", "item 2"), actual);

        //push some new data
        actual.clear();
        Files.writeString(tempFile, """
                        item 3
                        item 4
                        """,
                StandardOpenOption.SYNC, StandardOpenOption.APPEND);
        targetQueue.drainTo(actual, 100);
        Assertions.assertTrue(actual.isEmpty());


        fileEventSource.doWork();
        targetQueue.drainTo(actual, 100);
        Assertions.assertIterableEquals(List.of("item 3", "item 4"), actual);


//      ----------- event log --------------
        Assertions.assertIterableEquals(
                List.of("item 1", "item 2", "item 3", "item 4"),
                eventToQueue.getEventLog().stream().map(NamedFeedEvent::data).collect(Collectors.toList()));
    }
}
