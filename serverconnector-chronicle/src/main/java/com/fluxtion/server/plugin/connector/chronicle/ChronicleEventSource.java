/*
 *
 *  * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 *  * SPDX-License-Identifier: AGPL-3.0-only
 *
 */

package com.fluxtion.server.plugin.connector.chronicle;

import com.fluxtion.server.service.AbstractAgentHostedEventSourceService;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import net.openhft.chronicle.bytes.MethodReader;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueue;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import org.jetbrains.annotations.NotNull;

@Log4j2
@SuppressWarnings("all")
public class ChronicleEventSource extends AbstractAgentHostedEventSourceService {

    @Getter
    @Setter
    private String chroniclePath;
    private @NotNull MethodReader methodReader;

    public ChronicleEventSource(String name) {
        super(name);
    }

    public ChronicleEventSource() {
        this("chronicle-event-source");
    }

    @Override
    public void onStart() {
        SingleChronicleQueue queue_en = SingleChronicleQueueBuilder.binary(chroniclePath).build();
        MessageSink messageSink = output::publish;
        methodReader = queue_en.createTailer().methodReader(messageSink);
    }

    @Override
    public int doWork() throws Exception {
        int count = 0;
        while (methodReader.readOne()) {
            count++;
        }
        return count;
    }
}
