/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.connector.chronicle;

public class ChronicleEventSourceTest {
//    @TempDir
//    Path tempDir;

    //    @Test
//    @Disabled
//    public void testReadEvents() throws Exception {
//        //output to chronicle
//        ChronicleMessageSink chronicleMessageSink = new ChronicleMessageSink();
//        chronicleMessageSink.setChroniclePath(tempDir.toAbsolutePath().toString());
//        chronicleMessageSink.init();
//        chronicleMessageSink.start();
//        chronicleMessageSink.sendToSink("item 1");
//        chronicleMessageSink.sendToSink("item 2");
//
//        //read queue
//        ChronicleEventSource chronicleEventSource = new ChronicleEventSource();
//        chronicleEventSource.setCacheEventLog(true);
//        chronicleEventSource.setChroniclePath(tempDir.toAbsolutePath().toString());
//
//        EventToQueuePublisher<String> eventToQueue = new EventToQueuePublisher<>("myQueue");
//        OneToOneConcurrentArrayQueue<String> targetQueue = new OneToOneConcurrentArrayQueue<>(100);
//        eventToQueue.addTargetQueue(targetQueue, "outputQueue");
//        chronicleEventSource.setOutput(eventToQueue);
//
//        chronicleEventSource.init();
//        chronicleEventSource.onStart();
//        chronicleEventSource.start();
//        chronicleEventSource.startComplete();
//        chronicleEventSource.doWork();
//
//        ArrayList<String> actual = new ArrayList<>();
//
//        targetQueue.drainTo(actual, 100);
//        Assertions.assertIterableEquals(List.of("item 1", "item 2"), actual);
//
//        //push some new data
//        actual.clear();
//        chronicleMessageSink.sendToSink("item 3");
//        chronicleMessageSink.sendToSink("item 4");
//        targetQueue.drainTo(actual, 100);
//        Assertions.assertTrue(actual.isEmpty());
//
//
//        chronicleEventSource.doWork();
//        targetQueue.drainTo(actual, 100);
//        Assertions.assertIterableEquals(List.of("item 3", "item 4"), actual);
//
//        //      ----------- event log --------------
//        Assertions.assertIterableEquals(
//                List.of("item 1", "item 2", "item 3", "item 4"),
//                eventToQueue.getEventLog().stream().map(NamedFeedEvent::data).collect(Collectors.toList()));
//    }
//
}
