/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.connector.chronicle;

import com.fluxtion.runtime.event.NamedFeedEvent;
import com.fluxtion.server.dispatch.EventToQueuePublisher;
import com.fluxtion.server.service.AbstractAgentHostedEventSourceService;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import net.openhft.chronicle.bytes.MethodReader;
import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueue;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

@Log4j2
@SuppressWarnings("all")
public class ChronicleEventSource extends AbstractAgentHostedEventSourceService {

    @Getter
    @Setter
    private String chroniclePath;
    private String chronicleQueuePath;
    private String chronicleMapPath;
    @Getter
    @Setter
    private ReadStrategy readStrategy = ReadStrategy.COMMITED;
    @Getter
    @Setter
    private boolean cacheEventLog = false;
    private boolean publishToQueue = false;
    private MethodReader methodReader;
    private ChronicleMap<Long, Long> readpointer;
    private @NotNull ExcerptTailer tailer;

    public ChronicleEventSource(String name) {
        super(name);
    }

    public ChronicleEventSource() {
        this("chronicle-event-source");
    }

    @SneakyThrows
    @Override
    public void onStart() {
        chronicleQueuePath = chroniclePath + "/chronicle-queue";
        chronicleMapPath = chroniclePath + "/chronicle-map";
        SingleChronicleQueue chronicleQueue = SingleChronicleQueueBuilder.binary(chronicleQueuePath).build();
        MessageSink messageSink = this::publish;
        tailer = chronicleQueue.createTailer();
        methodReader = tailer.methodReader(messageSink);
        File chronicleMapFile = new File(chronicleMapPath + "/readPointer.dat");
        chronicleMapFile.getAbsoluteFile().getParentFile().mkdirs();

        output.setCacheEventLog(cacheEventLog);

        readpointer = ChronicleMap
                .of(Long.class, Long.class)
                .name("country-map")
                .entries(50)
                .createPersistedTo(chronicleMapFile);
        switch (readStrategy) {
            case COMMITED -> tailer.moveToIndex(readpointer.getOrDefault(1l, 0l));
            case LATEST -> tailer.toEnd();
            case EARLIEST -> tailer.toStart();
        }

        if (cacheEventLog) {
            log.info("cacheEventLog: {}", cacheEventLog);
            doWork();
        }
    }

    @Override
    public void startComplete() {
        publishToQueue = true;
        output.dispatchCachedEventLog();
    }

    @Override
    public <T> NamedFeedEvent<T>[] eventLog() {
        List<NamedFeedEvent> eventLog = output.getEventLog();
        return eventLog.toArray(new NamedFeedEvent[0]);
    }

    @Override
    public int doWork() throws Exception {
        int count = 0;
        while (methodReader.readOne()) {
            count++;
        }
        readpointer.put(1l, tailer.index());
        return count;
    }

    private void publish(Object line) {
        if (publishToQueue) {
            log.debug("publish record:{}", line);
            output.publish(line);
        } else {
            log.debug("cache record:{}", line);
            output.cache(line);
        }
    }

    //for testing
    void setOutput(EventToQueuePublisher<String> eventToQueue) {
        this.output = eventToQueue;
    }
}
