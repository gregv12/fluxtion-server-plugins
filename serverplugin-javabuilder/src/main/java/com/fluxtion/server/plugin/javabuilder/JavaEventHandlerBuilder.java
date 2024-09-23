/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.javabuilder;

import com.fluxtion.agrona.concurrent.YieldingIdleStrategy;
import com.fluxtion.compiler.Fluxtion;
import com.fluxtion.compiler.FluxtionGraphBuilder;
import com.fluxtion.compiler.generation.compiler.classcompiler.StringCompilation;
import com.fluxtion.runtime.EventProcessor;
import com.fluxtion.runtime.annotations.feature.Experimental;
import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.runtime.audit.EventLogControlEvent;
import com.fluxtion.server.service.admin.AdminCommandRegistry;
import com.fluxtion.server.service.servercontrol.FluxtionServerController;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

@Experimental
@Log4j2
public class JavaEventHandlerBuilder {

    private FluxtionServerController serverController;
    private AdminCommandRegistry adminCommandRegistry;
    private boolean addEventAuditor = true;
    private EventLogControlEvent.LogLevel auditLogLevel;

    @ServiceRegistered
    public void adminRegistry(AdminCommandRegistry adminCommandRegistry, String name) {
        log.info("Admin registry: '{}' name: '{}'", adminCommandRegistry, name);
        this.adminCommandRegistry = adminCommandRegistry;
        adminCommandRegistry.registerCommand("javaLoader.compileProcessor", this::loadProcessor);
    }

    @ServiceRegistered
    public void fluxtionServer(FluxtionServerController serverController, String name) {
        log.info("FluxtionServerController name: '{}'", name);
        this.serverController = serverController;
    }

    private void loadProcessor(List<String> args, Consumer<String> out, Consumer<String> err) {
        if (args.size() < 2) {
            err.accept("Missing arguments provide java file location");
            return;
        }

        String javaSourceFiler = args.get(1);
        out.accept("loading java source from file:" + javaSourceFiler);

        Path javaSourceFilePath = Path.of(javaSourceFiler);
        if (!javaSourceFilePath.toFile().exists()) {
            err.accept("File not found: " + javaSourceFiler);
            return;
        }


        //TODO - ensure unique fqn for the generated processor
        try {
            CompilationUnit cu = StaticJavaParser.parse(javaSourceFilePath);
            ClassOrInterfaceDeclaration classDeclaration = cu.findFirst(ClassOrInterfaceDeclaration.class).orElse(null);
            if (classDeclaration != null) {
                String className = classDeclaration.getFullyQualifiedName().get();
                out.accept("compiling builder class: " + className);
                Class<FluxtionGraphBuilder> compiledClass = StringCompilation.compile(className, Files.readString(javaSourceFilePath));

                final FluxtionGraphBuilder newInstance = compiledClass.getDeclaredConstructor().newInstance();
                EventProcessor eventProcessor = Fluxtion.compile(newInstance::buildGraph, newInstance::configureGeneration);

                eventProcessor.init();

                out.accept("compiled and loaded processor" + eventProcessor.toString());
                serverController.addEventProcessor("javaGroup", new YieldingIdleStrategy(), () -> eventProcessor);
            }
        } catch (IOException | ClassNotFoundException | URISyntaxException | InvocationTargetException |
                 InstantiationException | IllegalAccessException | NoSuchMethodException e) {
            err.accept("Failed to compile java source file: " + javaSourceFiler);
            log.error(e);
        }
    }
}
