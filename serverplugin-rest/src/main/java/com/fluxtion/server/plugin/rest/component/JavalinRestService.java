/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.rest.component;

import com.fluxtion.runtime.annotations.Start;
import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.runtime.lifecycle.Lifecycle;
import com.fluxtion.server.dispatch.EventFlowManager;
import com.fluxtion.server.dispatch.EventFlowService;
import com.fluxtion.server.plugin.rest.service.CommandProcessor;
import com.fluxtion.server.plugin.rest.service.RestController;
import com.fluxtion.server.service.admin.AdminCommandRegistry;
import com.fluxtion.server.service.admin.AdminCommandRequest;
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
    private AdminCommandRegistry adminCommandRegistry;

    @Override
    public void setEventFlowManager(EventFlowManager eventFlowManager, String serviceName) {
        log.info("set eventFlowManager name:'{}' for Javalin REST", serviceName);
        this.eventFlowManager = eventFlowManager;
    }

    @ServiceRegistered
    public void adminRegistry(AdminCommandRegistry adminCommandRegistry, String name) {
        log.info("Admin registry: '{}' name: '{}'", adminCommandRegistry, name);
        this.adminCommandRegistry = adminCommandRegistry;
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
                        ctx.json(new Message("registered queues - " + sb.toString()));
                    }
                })
                .post("/admin", ctx -> {
                    AdminCommandRequest adminCommandRequest = ctx.bodyAsClass(AdminCommandRequest.class);
                    adminCommandRequest.setOutput(out -> ctx.json(new Message(out.toString())));
                    adminCommandRequest.setErrOutput(out -> ctx.json(new Message("Failure - " + out)));
                    log.info("adminCommandRequest: {}", adminCommandRequest);
                    if (adminCommandRegistry != null) {
                        adminCommandRegistry.processAdminCommandRequest(adminCommandRequest);
                    }
                })
                .start(8080);
    }

    @Start
    public void start() {
        log.info("starting Javalin REST service");
    }

    @Override
    public void tearDown() {
        log.info("tear down Javalin REST service");
        javalin.stop();
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

    @Data
    public static class AdminCommand {
        private String command;
        private String[] args = new String[0];
    }
}
