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
import java.util.Objects;

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
                    AdminCommandRequest adminCommandRequest;
                    try {
                        adminCommandRequest = ctx.bodyAsClass(AdminCommandRequest.class);
                    } catch (Exception e) {
                        log.warn("Failed to parse body as AdminCommandRequest, using default instance", e);
                        adminCommandRequest = new AdminCommandRequest();
                    }
                    adminCommandRequest.setOutput(out -> {
                        String msg = Objects.toString(out, "");
                        msg = msg.replace("\"", "\\\"");
                        ctx.contentType("application/json");
                        ctx.result("{\"message\":\"" + msg + "\"}");
                        ctx.status(200);
                    });
                    adminCommandRequest.setErrOutput(out -> {
                        String msg = "Failure - " + Objects.toString(out, "");
                        msg = msg.replace("\"", "\\\"");
                        ctx.contentType("application/json");
                        ctx.result("{\"message\":\"" + msg + "\"}");
                        ctx.status(200);
                    });
                    log.info("adminCommandRequest: {}", adminCommandRequest);
                    if (adminCommandRegistry != null) {
                        adminCommandRegistry.processAdminCommandRequest(adminCommandRequest);
                    } else {
                        // Ensure a 200 response even if no registry is wired
                        ctx.status(200);
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
