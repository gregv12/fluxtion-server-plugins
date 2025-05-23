/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.connector.file;

import com.fluxtion.runtime.lifecycle.Lifecycle;
import com.fluxtion.runtime.output.AbstractMessageSink;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

@Log4j2
public class FileMessageSink extends AbstractMessageSink<Object>
        implements Lifecycle {

    @Getter
    @Setter
    private String filename;
    private PrintStream printStream;

    @Override
    public void init() {
    }

    @SneakyThrows
    @Override
    public void start() {
        Path path = Paths.get(filename);
        path.toFile().getParentFile().mkdirs();
        printStream = new PrintStream(
                Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND),
                false,
                StandardCharsets.UTF_8
        );
    }

    @Override
    protected void sendToSink(Object value) {
        log.trace("sink publish:{}", value);
        printStream.println(value);
    }

    @Override
    public void stop() {
        printStream.flush();
        printStream.close();
    }

    @Override
    public void tearDown() {
        stop();
    }
}
