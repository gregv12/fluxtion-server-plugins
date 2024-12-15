/*
 *
 *  * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 *  * SPDX-License-Identifier: AGPL-3.0-only
 *
 */

package com.fluxtion.server.plugin.connector.chronicle;

import com.fluxtion.runtime.lifecycle.Lifecycle;
import com.fluxtion.runtime.output.AbstractMessageSink;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueue;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;

@Log4j2
public class ChronicleMessageSink extends AbstractMessageSink<Object>
        implements Lifecycle {

    @Getter
    @Setter
    private String chroniclePath;
    private MessageSink messageSink;
    private SingleChronicleQueue chronicleQueue;

    @Override
    public void init() {
        String chronicleQueuePath = chroniclePath + "/chronicle-queue";
        chronicleQueue = SingleChronicleQueueBuilder.binary(chronicleQueuePath).build();
        messageSink = chronicleQueue.createAppender().methodWriter(MessageSink.class);
    }

    @Override
    protected void sendToSink(Object value) {
        log.trace("sink publish:{}", value);
        messageSink.onEvent(value);
    }

    @Override
    public void tearDown() {
        stop();
        chronicleQueue.clear();
    }
}
