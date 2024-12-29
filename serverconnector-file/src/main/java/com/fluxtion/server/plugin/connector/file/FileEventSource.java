/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.connector.file;

import com.fluxtion.agrona.IoUtil;
import com.fluxtion.server.service.AbstractAgentHostedEventSourceService;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

import java.io.*;
import java.nio.MappedByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

@Log4j2
@SuppressWarnings("all")
public class FileEventSource extends AbstractAgentHostedEventSourceService {

    @Getter
    @Setter
    private String filename;
    private InputStream stream;
    private BufferedReader reader = null;
    private char[] buffer;
    private int offset = 0;
    @Getter
    @Setter
    private int batchSize;
    @Getter
    @Setter
    private boolean publishOnStart = false;
    @Getter
    @Setter
    private ReadStrategy readStrategy = ReadStrategy.COMMITED;
    private boolean tail = true;
    private boolean commitRead = true;
    private boolean latestRead = false;
    private final AtomicBoolean startComplete = new AtomicBoolean(false);

    private long streamOffset;
    private MappedByteBuffer commitPointer;
    private boolean once;

    public FileEventSource() {
        this(1024);
    }

    /* visible for testing */
    public FileEventSource(int initialBufferSize) {
        super("fileEventFeed");
        buffer = new char[initialBufferSize];
    }

    @Override
    public void onStart() {
        log.info("agent onStart FileEventSource");
    }

    @Override
    public void start() {
        log.info("start FileEventSource");
        tail = readStrategy == ReadStrategy.COMMITED | readStrategy == ReadStrategy.EARLIEST | readStrategy == ReadStrategy.LATEST;
        once = !tail;
        commitRead = readStrategy == ReadStrategy.COMMITED;
        latestRead = readStrategy == ReadStrategy.LATEST | readStrategy == ReadStrategy.ONCE_LATEST;
        log.info("startComplete FileEventFeed tail:{} once:{}, commitRead:{} latestRead:{} readStrategy:{}", tail, once, commitRead, latestRead, readStrategy);

        File committedReadFile = new File(filename + ".readPointer");
        if (readStrategy == ReadStrategy.ONCE_EARLIEST | readStrategy == ReadStrategy.EARLIEST) {
            streamOffset = 0;
        } else if (committedReadFile.exists()) {
            commitPointer = IoUtil.mapExistingFile(committedReadFile, "committedReadFile_" + filename);
            streamOffset = commitPointer.getLong(0);
            log.info("{} reading committedReadFile:{}, streamOffset:{}", serviceName, committedReadFile.getAbsolutePath(), streamOffset);
        } else if (commitRead) {
            commitPointer = IoUtil.mapNewFile(committedReadFile, 1024);
            streamOffset = 0;
            log.info("{} creating committedReadFile:{}, streamOffset:{}", serviceName, committedReadFile.getAbsolutePath(), streamOffset);
        }

        if (filename == null || filename.isEmpty()) {
            //throw an  error
        }
        connectReader();
        tail = true;

        if (publishOnStart) {
            startComplete.set(true);
            log.info("publishOnStart: {}", publishOnStart);
            doWork();
            startComplete.set(false);
        }
    }

    @Override
    public void startComplete() {
        log.info("startComplete FileEventSource");
        startComplete.set(true);
        log.info("startComplete - exit");
    }

    @SuppressWarnings("all")
    @Override
    public int doWork() {
        if (!tail) {
            return 0;
        }
        try {
            if (connectReader() == null) {
                return 0;
            }
            log.debug("doWork FileEventFeed");

            ArrayList<String> records = null;

            int nread;
            while (reader.ready()) {
                tail = !once;
                Scanner scanner = new Scanner(reader);
                nread = reader.read(buffer, offset, buffer.length - offset);
                log.trace("Read {} bytes from {}", nread, getFilename());

                if (nread > 0) {
                    offset += nread;
                    String line;
                    boolean foundOneLine = false;
                    do {
                        line = extractLine();
                        if (line != null) {
                            foundOneLine = true;
                            log.trace("Read a line from {} line:'{}'", getFilename(), line);
                            if (records == null) {
                                records = new ArrayList<>();
                                if (latestRead) {
                                    records.add(line);
                                }
                            }
                            if (latestRead) {
                                if (records.isEmpty()) {
                                    records.add(line);
                                }
                                records.set(0, line);
                            } else {
                                records.add(line);
                            }

                            if (records.size() >= batchSize & !latestRead) {
                                var recordBatch = records;
                                records = new ArrayList<>();
                                output.publish(recordBatch);
                                log.info("publish batch:{}", recordBatch);
                                if (commitRead) {
                                    commitPointer.force();
                                }
                            }
                        }
                    } while (line != null);

                    if (latestRead & foundOneLine & !once) {
                        log.info("publish latest:{}", records);
                        output.publish(records);
                    } else if (foundOneLine && records.size() > 0) {
                        output.publish(records);
                        log.info("publish batch:{}", records);
                        records.clear();
                        if (commitRead) {
                            commitPointer.force();
                        }
                    }

                    if (!foundOneLine && offset == buffer.length) {
                        char[] newbuf = new char[buffer.length * 2];
                        System.arraycopy(buffer, 0, newbuf, 0, buffer.length);
                        log.info("Increased buffer from {} to {}", buffer.length, newbuf.length);
                        buffer = newbuf;
                    }
                }
            }

            return records == null ? 0 : records.size();

        } catch (IOException e) {
            try {
                reader.close();
            } catch (IOException ex) {

            }
            try {
                stream.close();
            } catch (IOException ex) {

            }
            reader = null;
            stream = null;
        }
        return 0;
    }

    @Override
    public void stop() {
        log.trace("Stopping");
        try {
            if (stream != null) {
                stream.close();
                log.trace("Closed input stream");
            }
        } catch (IOException e) {
            log.error("Failed to close FileStreamSourceTask stream: ", e);
        } finally {
            if (commitPointer != null) {
                commitPointer.force();
                IoUtil.unmap(commitPointer);
            }
        }
    }

    @Override
    public void tearDown() {
        super.tearDown();
    }

    private Reader connectReader() {
        if (startComplete.get() & stream == null && filename != null && !filename.isEmpty()) {
            try {
                stream = Files.newInputStream(Paths.get(filename));
                log.info("Found previous offset, trying to skip to file offset {}", streamOffset);
                long skipLeft = streamOffset;
                while (skipLeft > 0) {
                    try {
                        long skipped = stream.skip(skipLeft);
                        skipLeft -= skipped;
                    } catch (IOException e) {
                        log.error("Error while trying to seek to previous offset in file {}: ", filename, e);
                        //TODO log error and stop
                    }
                }
                log.info("Skipped to offset {}", streamOffset);
                reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
                log.info("Opened {} for reading", getFilename());
            } catch (NoSuchFileException e) {
                log.warn("Couldn't find file {} for FileStreamSourceTask, sleeping to wait for it to be created", getFilename());
            } catch (IOException e) {
                log.error("Error while trying to open file {}: ", filename, e);
                throw new RuntimeException(e);
            }
        }
        return reader;
    }

    private String extractLine() {
        int until = -1, newStart = -1;
        for (int i = 0; i < offset; i++) {
            if (buffer[i] == '\n') {
                until = i;
                newStart = i + 1;
                break;
            } else if (buffer[i] == '\r') {
                // We need to check for \r\n, so we must skip this if we can't check the next char
                if (i + 1 >= offset)
                    return null;

                until = i;
                newStart = (buffer[i + 1] == '\n') ? i + 2 : i + 1;
                break;
            }
        }

        if (until != -1) {
            String result = new String(buffer, 0, until);
            System.arraycopy(buffer, newStart, buffer, 0, buffer.length - newStart);
            offset = offset - newStart;
            streamOffset += newStart;
            if (commitRead) {
                commitPointer.putLong(0, streamOffset);
            }
            return result;
        } else {
            return null;
        }
    }

}