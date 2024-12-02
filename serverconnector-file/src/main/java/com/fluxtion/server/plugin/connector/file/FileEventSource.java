/*
 *
 *  * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 *  * SPDX-License-Identifier: AGPL-3.0-only
 *  
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
    private final AtomicBoolean startComplete = new AtomicBoolean(false);

    private long streamOffset;
    private MappedByteBuffer commitPointer;

    public FileEventSource() {
        this(1024);
    }

    /* visible for testing */
    public FileEventSource(int initialBufferSize) {
        super("fileEventFeed");
        buffer = new char[initialBufferSize];
    }

    @Override
    public void tearDown() {
        super.tearDown();
    }

    @Override
    public void startComplete() {
        log.info("Starting FileEventFeed");
        File committedReadFile = new File(".", filename + ".readPointer");
        log.info("{} creating committedReadFile:{}", serviceName, committedReadFile.getAbsolutePath());
        if (committedReadFile.exists()) {
            commitPointer = IoUtil.mapExistingFile(committedReadFile, "committedReadFile_" + filename);
            streamOffset = commitPointer.getLong(0);
        } else {
            commitPointer = IoUtil.mapNewFile(committedReadFile, 1024);
            streamOffset = 0;
        }
        startComplete.set(true);
        if (filename == null || filename.isEmpty()) {
            //throw an  error
        }
        connectReader();
    }

    @Override
    public void onStart() {
        log.info("Agent onStart FileEventFeed");
        super.onStart();
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
            commitPointer.force();
            IoUtil.unmap(commitPointer);
        }
    }

    @SuppressWarnings("all")
    @Override
    public int doWork() {
        try {
            if (connectReader() == null) {
                return 0;
            }

            ArrayList<SourceRecord<String>> records = null;

            int nread;
            while (reader.ready()) {
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
                            log.trace("Read a line from {}", getFilename());
                            if (records == null) {
                                records = new ArrayList<>();
                            }
                            records.add(new SourceRecord<>(serviceName, line));

                            if (records.size() >= batchSize) {
                                var recordBatch = records;
                                records = new ArrayList<>();
                                output.publish(recordBatch);
                                commitPointer.force();
                            }
                        }
                    } while (line != null);

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
            // Underlying stream was killed, probably as a result of calling stop. Allow to return
            // null, and driving thread will handle any shutdown if necessary.
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

    private Reader connectReader() {
        if (startComplete.get() & stream == null && filename != null && !filename.isEmpty()) {
            try {
                stream = Files.newInputStream(Paths.get(filename));
                log.debug("Found previous offset, trying to skip to file offset {}", streamOffset);
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
                log.debug("Skipped to offset {}", streamOffset);
                reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
                log.debug("Opened {} for reading", getFilename());
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
            commitPointer.putLong(0, streamOffset);
            return result;
        } else {
            return null;
        }
    }

}