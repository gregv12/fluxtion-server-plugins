/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.eventhandlerloader;

import com.fluxtion.agrona.concurrent.YieldingIdleStrategy;
import com.fluxtion.compiler.EventProcessorConfig;
import com.fluxtion.compiler.Fluxtion;
import com.fluxtion.runtime.EventProcessor;
import com.fluxtion.runtime.annotations.feature.Experimental;
import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.runtime.audit.Auditor;
import com.fluxtion.runtime.audit.EventLogControlEvent;
import com.fluxtion.runtime.partition.LambdaReflection.SerializableConsumer;
import com.fluxtion.server.service.admin.AdminCommandRegistry;
import com.fluxtion.server.service.servercontrol.FluxtionServerController;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Experimental
@Log4j2
public class EventHandlerLoader {

    private FluxtionServerController serverController;
    private static final String DEFAULT_GROUP_JAVA_SRC = "javaSourceLoader";
    private static final String DEFAULT_GROUP_YAML = "yamlLoader";

    @ServiceRegistered
    public void adminRegistry(AdminCommandRegistry adminCommandRegistry, String name) {
        log.info("Admin registry: '{}' name: '{}'", adminCommandRegistry, name);
        adminCommandRegistry.registerCommand("javaLoader.compileProcessor", this::compileProcessor);
        adminCommandRegistry.registerCommand("javaLoader.interpretProcessor", this::interpretProcessor);
        adminCommandRegistry.registerCommand("javaLoader.reloadInterpretProcessor", this::compileReloadProcessor);
        adminCommandRegistry.registerCommand("javaLoader.reloadCompileProcessor", this::interpretReloadProcessor);
        adminCommandRegistry.registerCommand("yamlLoader.compileProcessor", this::compileProcessorYaml);
        adminCommandRegistry.registerCommand("yamlLoader.interpretProcessor", this::interpretProcessorYaml);
    }

    @ServiceRegistered
    public void fluxtionServer(FluxtionServerController serverController, String name) {
        log.info("FluxtionServerController name: '{}'", name);
        this.serverController = serverController;
    }


    protected void interpretProcessor(List<String> args, Consumer<String> out, Consumer<String> err) {
        loadProcessor(false, args, out, err);
    }

    protected void compileProcessor(List<String> args, Consumer<String> out, Consumer<String> err) {
        loadProcessor(true, args, out, err);
    }

    private void interpretProcessorYaml(List<String> args, Consumer<String> out, Consumer<String> err) {
        loadProcessorYaml(false, args, out, err);
    }

    private void compileProcessorYaml(List<String> args, Consumer<String> out, Consumer<String> err) {
        loadProcessorYaml(true, args, out, err);
    }

    private void interpretReloadProcessor(List<String> args, Consumer<String> out, Consumer<String> err) {
        reloadProcessor(false, args, out, err);
    }

    private void compileReloadProcessor(List<String> args, Consumer<String> out, Consumer<String> err) {
        reloadProcessor(true, args, out, err);
    }

    private void reloadProcessor(boolean compileProcessor, List<String> args, Consumer<String> out, Consumer<String> err) {
        log.info("reloadProcessor");
        if (args.size() < 2) {
            err.accept("Missing arguments provide [group name]/[java source file location]");
            return;
        }

        String springFile = args.get(1);
        out.accept("stopping processor defined from file:" + springFile);

        String[] splitArgs = springFile.split("/");
        String groupName = splitArgs[0];
        String javaFile = springFile.substring(groupName.length());
        serverController.stopProcessor(groupName, javaFile);

        loadProcessor(compileProcessor, List.of("loadProcessor", javaFile, groupName), out, err);
    }

    private void loadProcessor(boolean compileProcessor, List<String> args, Consumer<String> out, Consumer<String> err) {
        if (args.size() < 2) {
            err.accept("Missing arguments provide java file location");
            return;
        }

        String group = args.size() > 2 ? args.get(2) : DEFAULT_GROUP_JAVA_SRC;

        String javaSourceFiler = args.get(1);
        out.accept("loading java source from file:" + javaSourceFiler);

        Path javaSourceFilePath = Path.of(javaSourceFiler);
        if (!javaSourceFilePath.toFile().exists()) {
            err.accept("File not found: " + javaSourceFiler);
            return;
        }

        try {
            EventProcessor<?> eventProcessor = DynamicCompiler.loadEventProcessor(compileProcessor, javaSourceFilePath, out, err);
//            FluxtionGraphBuilder fluxtionGraphBuilder = DynamicCompiler.compileBuilder(javaSourceFilePath, out, err);
            if (eventProcessor != null) {
//                SerializableConsumer<EventProcessorConfig> buildGraph = fluxtionGraphBuilder::buildGraph;
//                EventProcessor<?> eventProcessor = compileProcessor ? Fluxtion.compile(buildGraph) : Fluxtion.interpret(buildGraph);

                eventProcessor.init();

                out.accept("compiled and loaded processor" + eventProcessor.toString());
                serverController.addEventProcessor(javaSourceFiler, group, new YieldingIdleStrategy(), () -> eventProcessor);
            }
        } catch (IOException e) {
            err.accept("Failed to compile java source file: " + javaSourceFiler);
            log.error(e);
        }
    }

    private void loadProcessorYaml(boolean compileProcessor, List<String> args, Consumer<String> out, Consumer<String> err) {
        if (args.size() < 2) {
            err.accept("Missing arguments provide yaml file location");
            return;
        }

        String group = args.size() > 2 ? args.get(2) : DEFAULT_GROUP_YAML;

        String javaSourceFiler = args.get(1);

        Path javaSourceFilePath = Path.of(javaSourceFiler);
        if (!javaSourceFilePath.toFile().exists()) {
            err.accept("File not found: " + javaSourceFiler);
            return;
        }

        out.accept("loading yaml file:" + javaSourceFiler);

        try {
            Yaml yaml = new Yaml();
            EventProcessorYamlCfg yamlCfg = yaml.loadAs(Files.readString(javaSourceFilePath), EventProcessorYamlCfg.class);

            SerializableConsumer<EventProcessorConfig> buildGraph = cfg -> {
                yamlCfg.nodes.forEach(cfg::addNode);

                yamlCfg.namedNodes.entrySet().forEach(entry -> {
                    cfg.addNode(entry.getValue(), entry.getKey());
                });

                cfg.setSupportDirtyFiltering(yamlCfg.isCheckDirtyFlags());

                yamlCfg.auditorMap.entrySet().forEach(entry -> {
                    cfg.addAuditor(entry.getValue(), entry.getKey());
                });

                cfg.addEventAudit(yamlCfg.logLevel);
            };

            EventProcessor<?> eventProcessor = compileProcessor ? Fluxtion.compile(buildGraph) : Fluxtion.interpret(buildGraph);
            eventProcessor.init();

            out.accept("compiled and loaded processor" + eventProcessor.toString());
            serverController.addEventProcessor(javaSourceFiler, group, new YieldingIdleStrategy(), () -> eventProcessor);
        } catch (Exception e) {
            err.accept("Failed to compile java source file: " + javaSourceFiler);
            log.error(e);
        }
    }

    @Data
    public static class EventProcessorYamlCfg {
        private List<Object> nodes = new ArrayList<>();
        private Map<String, Object> namedNodes = new HashMap<>();
        private EventLogControlEvent.LogLevel logLevel = EventLogControlEvent.LogLevel.NONE;
        private boolean checkDirtyFlags = true;
        private HashMap<String, Auditor> auditorMap = new HashMap<>();
    }
}
