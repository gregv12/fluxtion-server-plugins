/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.rest.component;

import com.fluxtion.runtime.annotations.Start;
import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.runtime.lifecycle.Lifecycle;
import com.fluxtion.server.service.admin.AdminCommandRegistry;
import com.fluxtion.server.service.admin.AdminCommandRequest;
import io.javalin.Javalin;
import io.javalin.http.Context;
import lombok.*;
import lombok.extern.log4j.Log4j2;

import java.util.Objects;

@Log4j2
public class JavalinAdminCommandService implements Lifecycle {

    private Javalin javalin;
    private AdminCommandRegistry adminCommandRegistry;
    @Getter
    @Setter
    private int listenPort = 8080;

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
                    final AdminCommandRequest adminCommandRequest;
                    try {
                        adminCommandRequest = ctx.bodyAsClass(AdminCommandRequest.class);
                    } catch (Exception e) {
                        // A malformed body (e.g. the common mistake of sending "args" instead of the
                        // "arguments" field) previously fell through to a null command and surfaced as a
                        // bare HTTP 500. Report it as a helpful 400 that names the expected shape.
                        ctx.status(400).json(new Message(
                                "invalid request body; expected {\"command\":\"<name>\",\"arguments\":[...]} "
                                        + "(the argument list field is 'arguments', not 'args') - " + e.getMessage()));
                        return;
                    }
                    if (adminCommandRequest.getCommand() == null || adminCommandRequest.getCommand().isBlank()) {
                        ctx.status(400).json(new Message(
                                "missing 'command'; body must be {\"command\":\"<name>\",\"arguments\":[...]}"));
                        return;
                    }
                    // Reply via ctx.json so newlines, quotes and other control characters in the command
                    // output are correctly escaped — the response is always valid JSON (callers no longer
                    // need a lenient parser).
                    adminCommandRequest.setOutput(out -> writeMessage(ctx, Objects.toString(out, "")));
                    adminCommandRequest.setErrOutput(out -> writeMessage(ctx, "Failure - " + Objects.toString(out, "")));
                    log.info("adminCommandRequest: {}", adminCommandRequest);
                    if (adminCommandRegistry != null) {
                        adminCommandRegistry.processAdminCommandRequest(adminCommandRequest);
                    } else {
                        ctx.status(503).json(new Message("admin command registry not available"));
                    }
                })
                .start(listenPort);
    }

    /** Write {@code {"message": ...}} with correct JSON escaping (ctx.json handles control chars). */
    private void writeMessage(Context ctx, String message) {
        ctx.status(200).json(new Message(message));
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

    /**
     * @deprecated the wire contract is {@link AdminCommandRequest}, whose argument field is
     * {@code arguments}. This type's {@code args} field is a common source of confusion and is not
     * used by the endpoint; kept only for backwards source compatibility.
     */
    @Deprecated
    @Data
    public static class AdminCommand {
        private String command;
        private String[] args = new String[0];
    }
}
