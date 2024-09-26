/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.spring;

import com.fluxtion.agrona.concurrent.YieldingIdleStrategy;
import com.fluxtion.compiler.extern.spring.FluxtionSpring;
import com.fluxtion.runtime.EventProcessor;
import com.fluxtion.runtime.annotations.feature.Preview;
import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.runtime.audit.EventLogControlEvent;
import com.fluxtion.server.service.admin.AdminCommandRegistry;
import com.fluxtion.server.service.servercontrol.FluxtionServerController;
import lombok.extern.log4j.Log4j2;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

@Preview
@Log4j2
public class SpringEventHandlerLoader {

    private FluxtionServerController serverController;
    private AdminCommandRegistry adminCommandRegistry;
    private boolean addEventAuditor = true;
    private EventLogControlEvent.LogLevel auditLogLevel;
    private static final String DEFAULT_GROUP = "springBeanLoader";


    @ServiceRegistered
    public void adminRegistry(AdminCommandRegistry adminCommandRegistry, String name) {
        log.info("Admin registry: '{}' name: '{}'", adminCommandRegistry, name);
        this.adminCommandRegistry = adminCommandRegistry;
        adminCommandRegistry.registerCommand("springLoader.compileProcessor", this::compileProcessor);
        adminCommandRegistry.registerCommand("springLoader.interpretProcessor", this::interpretProcessor);
    }

    @ServiceRegistered
    public void fluxtionServer(FluxtionServerController serverController, String name) {
        log.info("FluxtionServerController name: '{}'", name);
        this.serverController = serverController;
    }

    private void interpretProcessor(List<String> args, Consumer<String> out, Consumer<String> err) {
        loadProcessor(false, args, out, err);
    }

    private void compileProcessor(List<String> args, Consumer<String> out, Consumer<String> err) {
        loadProcessor(true, args, out, err);
    }


    private void loadProcessor(boolean compileProcessor, List<String> args, Consumer<String> out, Consumer<String> err) {
        if (args.size() < 2) {
            err.accept("Missing arguments provide spring bean file location");
            return;
        }

        String group = args.size() > 2 ? args.get(2) : DEFAULT_GROUP;

        String springFile = args.get(1);
        out.accept("loading config from file:" + springFile);

        Path springFilePath = Path.of(springFile);
        if (!springFilePath.toFile().exists()) {
            err.accept("File not found: " + springFile);
            return;
        }

        EventProcessor<?> eventProcessor;
        if (compileProcessor) {
            eventProcessor = FluxtionSpring.compile(springFilePath, cfg -> {
                if (addEventAuditor) {
                    cfg.addEventAudit(auditLogLevel != null ? auditLogLevel : EventLogControlEvent.LogLevel.INFO);
                }
            });
        } else {
            eventProcessor = FluxtionSpring.interpret(springFilePath, cfg -> {
                if (addEventAuditor) {
                    cfg.addEventAudit(auditLogLevel != null ? auditLogLevel : EventLogControlEvent.LogLevel.INFO);
                }
            });
        }

        eventProcessor.init();

        out.accept("compiled and loaded processor" + eventProcessor.toString());
        serverController.addEventProcessor(group, new YieldingIdleStrategy(), () -> eventProcessor);

    }
}
