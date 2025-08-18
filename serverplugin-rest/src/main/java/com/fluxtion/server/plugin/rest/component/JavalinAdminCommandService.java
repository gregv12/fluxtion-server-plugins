/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.rest.component;

import com.fluxtion.runtime.annotations.Start;
import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.runtime.lifecycle.Lifecycle;
import com.fluxtion.server.dispatch.EventFlowManager;
import com.fluxtion.server.service.EventFlowService;
import com.fluxtion.server.service.admin.AdminCommandRegistry;
import com.fluxtion.server.service.admin.AdminCommandRequest;
import io.javalin.Javalin;
import lombok.*;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class JavalinAdminCommandService implements EventFlowService, Lifecycle {

    private Javalin javalin;
    private EventFlowManager eventFlowManager;
    private AdminCommandRegistry adminCommandRegistry;
    @Getter
    @Setter
    private int listenPort = 8080;

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
        log.info("init Javalin REST service listening on port {}", listenPort);
        javalin = Javalin.create()
                .post("/admin", ctx -> {
                    AdminCommandRequest adminCommandRequest = ctx.bodyAsClass(AdminCommandRequest.class);
                    adminCommandRequest.setOutput(out -> ctx.json(new Message(out.toString())));
                    adminCommandRequest.setErrOutput(out -> ctx.json(new Message("Failure - " + out)));
                    log.info("adminCommandRequest: {}", adminCommandRequest);
                    if (adminCommandRegistry != null) {
                        adminCommandRegistry.processAdminCommandRequest(adminCommandRequest);
                    }
                })
                .start(listenPort);
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
