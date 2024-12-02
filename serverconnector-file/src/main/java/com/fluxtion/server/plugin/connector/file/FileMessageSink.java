/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.connector.file;

import com.fluxtion.runtime.lifecycle.Lifecycle;
import com.fluxtion.runtime.output.MessageSink;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

@Log4j2
public class FileMessageSink  implements Lifecycle, MessageSink<Object> {

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
        printStream = new PrintStream(
                Files.newOutputStream(Paths.get(filename), StandardOpenOption.CREATE, StandardOpenOption.APPEND),
                false,
                StandardCharsets.UTF_8
        );
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


    @Override
    public void accept(Object o) {
        log.info("sink publish:{}", o);
        printStream.println(o);
    }
}
