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

import com.fluxtion.runtime.event.NamedFeedEvent;
import com.fluxtion.server.dispatch.EventToQueuePublisher;
import com.fluxtion.server.service.extension.AbstractAgentHostedEventSourceService;
import io.aeron.Aeron;
import io.aeron.FragmentAssembler;
import io.aeron.Subscription;
import io.aeron.archive.client.AeronArchive;
import io.aeron.driver.MediaDriver;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Log4j2
@SuppressWarnings("all")
public class AeronArchiveEventSource extends AbstractAgentHostedEventSourceService {

    public enum Mode {LIVE, ARCHIVE}

    @Getter
    @Setter
    private Mode mode = Mode.LIVE;
    @Getter
    @Setter
    private String channel = "aeron:ipc";
    @Getter
    @Setter
    private int streamId = 10;
    @Getter
    @Setter
    private String replayChannel = "aeron:ipc"; // archive replay destination
    @Getter
    @Setter
    private String aeronDirectoryName;
    @Getter
    @Setter
    private boolean launchEmbeddedDriver = false;
    @Getter
    @Setter
    private boolean cacheEventLog = false;

    private volatile boolean publishToQueue = false;

    private MediaDriver mediaDriver;
    private Aeron aeron;
    private Subscription subscription;
    private AeronArchive archive;

    private final FragmentAssembler fragmentAssembler = new FragmentAssembler((buffer, offset, length, header) -> {
        byte[] data = new byte[length];
        for (int i = 0; i < length; i++) {
            data[i] = buffer.getByte(offset + i);
        }
        String msg = new String(data, StandardCharsets.UTF_8);
        publish(msg);
    });

    public AeronArchiveEventSource() {
        this("aeron-archive-event-source");
    }

    public AeronArchiveEventSource(String name) {
        super(name);
    }

    @Override
    public void onStart() {
        try {
            if (launchEmbeddedDriver) {
                MediaDriver.Context ctx = new MediaDriver.Context().dirDeleteOnStart(true).dirDeleteOnShutdown(true);
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

            output.setCacheEventLog(cacheEventLog);

            if (mode == Mode.LIVE) {
                subscription = aeron.addSubscription(channel, streamId);
            } else {
                // ARCHIVE mode: try to replay the latest recording for channel/stream
                archive = AeronArchive.connect(new AeronArchive.Context().aeron(aeron));
                long recordingId = findLatestRecordingId(archive, channel, streamId);
                if (recordingId == Aeron.NULL_VALUE) {
                    log.warn("No recording found for channel:{} stream:{} - archive mode will be idle", channel, streamId);
                } else {
                    long replaySession = archive.startReplay(recordingId, 0, Long.MAX_VALUE, replayChannel, streamId);
                    subscription = aeron.addSubscription(replayChannel, streamId);
                    log.info("Started archive replay recordingId:{} session:{} on {} stream:{}", recordingId, replaySession, replayChannel, streamId);
                }
            }

            if (cacheEventLog) {
                doWork();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to start AeronArchiveEventSource", e);
        }
    }

    private long findLatestRecordingId(AeronArchive archive, String channel, int streamId) {
        final long[] latest = {Aeron.NULL_VALUE};
        archive.listRecordings(0, Integer.MAX_VALUE, (controlSessionId, correlationId, recordingId, startTimestamp, stopTimestamp, startPosition, stopPosition, initialTermId, segmentFileLength, termBufferLength, mtuLength, sessionId, streamId1, strippedChannel, originalChannel, sourceIdentity) -> {
            if (streamId1 == streamId && originalChannel.contains(channel)) {
                if (latest[0] == Aeron.NULL_VALUE || recordingId > latest[0]) {
                    latest[0] = recordingId;
                }
            }
        });
        return latest[0];
    }

    @Override
    public void startComplete() {
        publishToQueue = true;
        output.dispatchCachedEventLog();
    }

    @Override
    public <T> NamedFeedEvent<T>[] eventLog() {
        List<NamedFeedEvent> eventLog = output.getEventLog();
        return (NamedFeedEvent<T>[]) eventLog.toArray(new NamedFeedEvent[0]);
    }

    @Override
    public int doWork() {
        if (subscription == null) return 0;
        return subscription.poll(fragmentAssembler, 50);
    }

    private void publish(Object o) {
        if (publishToQueue) {
            output.publish(o);
        } else {
            output.cache(o);
        }
    }

    @Override
    public void tearDown() {
        try {
            if (subscription != null) subscription.close();
        } catch (Exception ignored) {
        }
        try {
            if (archive != null) archive.close();
        } catch (Exception ignored) {
        }
        try {
            if (aeron != null) aeron.close();
        } catch (Exception ignored) {
        }
        try {
            if (mediaDriver != null) mediaDriver.close();
        } catch (Exception ignored) {
        }
    }

    // for testing
    void setOutput(EventToQueuePublisher<String> eventToQueue) {
        this.output = eventToQueue;
    }
}
