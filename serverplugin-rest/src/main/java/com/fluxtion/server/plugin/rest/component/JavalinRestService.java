/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.rest.component;

import com.fluxtion.runtime.lifecycle.Lifecycle;
import com.fluxtion.server.dispatch.EventFlowManager;
import com.fluxtion.server.dispatch.EventFlowService;
import com.fluxtion.server.plugin.rest.service.CommandProcessor;
import com.fluxtion.server.plugin.rest.service.RestController;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

@Log4j2
public class JavalinRestService implements EventFlowService, Lifecycle, RestController {

    private Javalin javalin;
    private EventFlowManager eventFlowManager;

    @Override
    public void setEventFlowManager(EventFlowManager eventFlowManager, String serviceName) {
        log.info("Setting up Javalin REST service name:'{}'", serviceName);
        this.eventFlowManager = eventFlowManager;
    }

    @Override
    public void init() {
        log.info("init Javalin REST service");
        javalin = Javalin.create()
                .get("/queues", new Handler() {
                    @Override
                    public void handle(@NotNull Context ctx) throws Exception {
                        StringBuilder sb = new StringBuilder();
                        eventFlowManager.appendQueueInformation(sb);
                        ctx.json(new Message(sb.toString()));
                    }
                })
                .start(8080);
    }

    @Override
    public void tearDown() {
        log.info("tear down Javalin REST service");
    }

    @Override
    public <S, T> void addCommand(String command, CommandProcessor<S, T> commandProcessor) {
        log.info("add command:'{}'", command);
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Message {
        private String message;
    }
}
