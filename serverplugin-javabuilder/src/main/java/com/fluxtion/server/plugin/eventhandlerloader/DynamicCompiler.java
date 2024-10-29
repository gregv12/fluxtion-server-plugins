/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.eventhandlerloader;

import com.fluxtion.agrona.generation.CompilerUtil;
import com.fluxtion.compiler.EventProcessorConfig;
import com.fluxtion.compiler.Fluxtion;
import com.fluxtion.compiler.FluxtionGraphBuilder;
import com.fluxtion.runtime.EventProcessor;
import com.fluxtion.runtime.partition.LambdaReflection;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;

public class DynamicCompiler {

    public static EventProcessor<?> loadEventProcessor(
            boolean compileProcessor,
            Path javaSourceFilePath,
            Consumer<String> out,
            Consumer<String> err) throws IOException {
        
        FluxtionGraphBuilder builder = DynamicCompiler.compileBuilder(
                javaSourceFilePath,
                System.out::println,
                System.err::println);

        LambdaReflection.SerializableConsumer<EventProcessorConfig> buildGraph = builder::buildGraph;
        return compileProcessor ? Fluxtion.compile(buildGraph) : Fluxtion.interpret(buildGraph);
    }

    public static FluxtionGraphBuilder compileBuilder(Path javaSourceFilePath, Consumer<String> out, Consumer<String> err) throws IOException {
        FluxtionGraphBuilder[] builder = new FluxtionGraphBuilder[1];
        String path = javaSourceFilePath.toAbsolutePath().toString();
        String simpleName = javaSourceFilePath.getFileName().toFile().getName().replace(".java", "");
        CompilationUnit cu = StaticJavaParser.parse(javaSourceFilePath);
        cu.findFirst(ClassOrInterfaceDeclaration.class, c -> c.getNameAsString().endsWith(simpleName))
                .map(ClassOrInterfaceDeclaration.class::cast)
                .ifPresent(classDeclaration -> {
                    String className = classDeclaration.getFullyQualifiedName().get();
                    out.accept("compiling builder class: " + className);
                    try {
                        @SuppressWarnings({"unchecked"})
                        Class<FluxtionGraphBuilder> compiledClass = (Class<FluxtionGraphBuilder>) CompilerUtil.compileInMemory(className, Map.of(className, Files.readString(javaSourceFilePath)));
                        builder[0] = compiledClass.getDeclaredConstructor().newInstance();
                    } catch (ClassNotFoundException | IOException | InvocationTargetException | NoSuchMethodException |
                             IllegalAccessException | InstantiationException e) {
                        err.accept("problem compiling FluxtionGraphBuilder path: " + path + "error:" + e.getMessage());
                    }
                });

        return builder[0];
    }
}

