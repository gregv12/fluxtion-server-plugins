/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.eventhandlerloader;

import com.fluxtion.agrona.concurrent.YieldingIdleStrategy;
import com.fluxtion.compiler.EventProcessorConfig;
import com.fluxtion.compiler.Fluxtion;
import com.fluxtion.compiler.FluxtionCompilerConfig;
import com.fluxtion.compiler.FluxtionGraphBuilder;
import com.fluxtion.compiler.generation.compiler.classcompiler.StringCompilation;
import com.fluxtion.runtime.EventProcessor;
import com.fluxtion.runtime.annotations.feature.Experimental;
import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.runtime.audit.Auditor;
import com.fluxtion.runtime.audit.EventLogControlEvent;
import com.fluxtion.runtime.lifecycle.Lifecycle;
import com.fluxtion.runtime.partition.LambdaReflection.SerializableConsumer;
import com.fluxtion.server.service.admin.AdminCommandRegistry;
import com.fluxtion.server.service.servercontrol.FluxtionServerController;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

@Experimental
@Log4j2
public class EventHandlerLoader implements Lifecycle {

    private FluxtionServerController serverController;
    private static final String DEFAULT_GROUP_JAVA_SRC = "javaSourceLoader";
    private static final String DEFAULT_GROUP_YAML = "yamlLoader";
    @Getter
    @Setter
    private Set<EventLoadAtStartup> loadAtStartup = new HashSet<>();
    private EventLogControlEvent.LogLevel initialLogLevel = EventLogControlEvent.LogLevel.INFO;
    private EventLogControlEvent.LogLevel traceLogLevel;
    private boolean addEventAuditor = false;

    @ServiceRegistered
    public void adminRegistry(AdminCommandRegistry adminCommandRegistry, String name) {
        log.info("Admin registry: '{}' name: '{}'", adminCommandRegistry, name);
        adminCommandRegistry.registerCommand("javaLoader.compileProcessor", this::compileProcessor);
        adminCommandRegistry.registerCommand("javaLoader.interpretProcessor", this::interpretProcessor);
        adminCommandRegistry.registerCommand("yamlLoader.compileProcessor", this::compileProcessorYaml);
        adminCommandRegistry.registerCommand("yamlLoader.interpretProcessor", this::interpretProcessorYaml);
    }

    @ServiceRegistered
    public void fluxtionServer(FluxtionServerController serverController, String name) {
        log.info("FluxtionServerController name: '{}'", name);
        this.serverController = serverController;
    }

    @Override
    public void init() {
    }

    @Override
    public void start() {
        log.info("Start yaml EventHandler loader startUpConfig:{}", loadAtStartup);
        loadAtStartup.forEach(cfg -> {
            initialLogLevel = cfg.getInitialLogLevel();
            traceLogLevel = cfg.getTraceLogLevel();
            addEventAuditor = cfg.isAddEventAuditor();
            loadProcessorYaml(cfg.isCompile(),
                    List.of("loadProcessor", cfg.getYamlFile(), cfg.getGroup()),
                    log::info,
                    log::error);
        });
        initialLogLevel = EventLogControlEvent.LogLevel.INFO;
        traceLogLevel = null;
        addEventAuditor = false;
    }

    @Override
    public void tearDown() {
    }

    private void interpretProcessor(List<String> args, Consumer<String> out, Consumer<String> err) {
        loadProcessor(false, args, out, err);
    }

    private void compileProcessor(List<String> args, Consumer<String> out, Consumer<String> err) {
        loadProcessor(true, args, out, err);
    }

    private void interpretProcessorYaml(List<String> args, Consumer<String> out, Consumer<String> err) {
        loadProcessorYaml(false, args, out, err);
    }

    private void compileProcessorYaml(List<String> args, Consumer<String> out, Consumer<String> err) {
        loadProcessorYaml(true, args, out, err);
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
            CompilationUnit cu = StaticJavaParser.parse(javaSourceFilePath);
            ClassOrInterfaceDeclaration classDeclaration = cu.findFirst(ClassOrInterfaceDeclaration.class).orElse(null);
            if (classDeclaration != null) {
                String className = classDeclaration.getFullyQualifiedName().get();
                out.accept("compiling builder class: " + className);
                Class<FluxtionGraphBuilder> compiledClass = StringCompilation.compile(className, Files.readString(javaSourceFilePath));

                SerializableConsumer<EventProcessorConfig> buildGraph = compiledClass.getDeclaredConstructor().newInstance()::buildGraph;
                EventProcessor<?> eventProcessor = compileProcessor ? Fluxtion.compile(buildGraph) : Fluxtion.interpret(buildGraph);

                eventProcessor.init();
                eventProcessor.setAuditLogLevel(initialLogLevel);

                out.accept("compiled and loaded processor" + eventProcessor.toString());
                serverController.addEventProcessor(javaSourceFiler, group, new YieldingIdleStrategy(), () -> eventProcessor);
            }
        } catch (IOException | ClassNotFoundException | URISyntaxException | InvocationTargetException |
                 InstantiationException | IllegalAccessException | NoSuchMethodException e) {
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

                if (addEventAuditor | yamlCfg.enableAudit) {
                    cfg.addEventAudit();
                    cfg.addEventAudit(traceLogLevel == null ? yamlCfg.traceLogeLevel : traceLogLevel);
                }

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
        private boolean enableAudit = false;
        private EventLogControlEvent.LogLevel traceLogeLevel = EventLogControlEvent.LogLevel.NONE;
        private boolean checkDirtyFlags = true;
        private HashMap<String, Auditor> auditorMap = new HashMap<>();
        private FluxtionCompilerConfig compilerConfig;
    }

    @Data
    public static final class EventLoadAtStartup {
        private String yamlFile;
        private String group = DEFAULT_GROUP_YAML;
        private boolean addEventAuditor = false;
        private boolean compile = true;
        private EventLogControlEvent.LogLevel traceLogLevel;
        private EventLogControlEvent.LogLevel initialLogLevel = EventLogControlEvent.LogLevel.INFO;
    }
}
