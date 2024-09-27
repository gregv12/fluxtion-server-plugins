/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.adminconsole;

import com.fluxtion.runtime.annotations.feature.Experimental;
import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.runtime.lifecycle.Lifecycle;
import com.fluxtion.server.service.admin.AdminCommandRegistry;
import com.fluxtion.server.service.admin.AdminCommandRequest;
import lombok.extern.java.Log;
import org.jline.reader.Candidate;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Experimental
@Log
public class CliAdminCommandProcessor implements Lifecycle {

    private AdminCommandRegistry adminCommandRegistry;

    @Override
    public void init() {
        log.info("init");
    }

    @ServiceRegistered
    public void adminRegistry(AdminCommandRegistry adminCommandRegistry, String name) {
        log.info("AdminCommandRegistry available name: " + name);
        this.adminCommandRegistry = adminCommandRegistry;
    }

    @Override
    public void start() {
        log.info("start");
        try {
            Terminal terminal = TerminalBuilder.builder()
                    .system(true)
                    .jni(true)
                    .streams(System.in, System.out)
                    .build();
            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .completer((reader1, line, candidates) -> {
                        for (String string : adminCommandRegistry.commandList()) {
                            candidates.add(new Candidate(AttributedString.stripAnsi(string), string, null, null, null, null, true));
                        }
                        candidates.add(new Candidate(AttributedString.stripAnsi("quit"), "quit", null, null, null, null, true));
                    })
                    .build();

            processCommand(adminCommandRegistry, terminal, new String[]{"?"});
            processCommand(adminCommandRegistry, terminal, new String[]{"commands"});
            while (true) {
                String line = reader.readLine("command > ").trim();
                if (line.equalsIgnoreCase("quit")) {
                    break;
                }

                String[] commandArgs = line.trim().split("\\s+");

                if (commandArgs.length > 0) {
                    reader.getHistory().add(line);
                    processCommand(adminCommandRegistry, terminal, commandArgs);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public void stop() {
        log.info("stop");
    }

    @Override
    public void tearDown() {
        log.info("stop");
    }

    private static void processCommand(AdminCommandRegistry adminCommandRegistry, Terminal terminal, String[] commandArgs) {
        AdminCommandRequest adminCommandRequest = new AdminCommandRequest();
        List<String> commandArgsList = new ArrayList<>(Arrays.asList(commandArgs));
        commandArgsList.remove(0);

        adminCommandRequest.setCommand(commandArgs[0]);
        adminCommandRequest.setArguments(commandArgsList);
        adminCommandRequest.setOutput(terminal.writer()::println);
        adminCommandRequest.setErrOutput(terminal.writer()::println);

        log.info("adminCommandRequest: " + adminCommandRequest);
        if (adminCommandRegistry != null) {
            adminCommandRegistry.processAdminCommandRequest(adminCommandRequest);
        }
    }
}

