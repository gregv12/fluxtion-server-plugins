/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.eventhandlerloader;

import com.fluxtion.compiler.EventProcessorConfig;
import com.fluxtion.compiler.Fluxtion;
import com.fluxtion.compiler.FluxtionGraphBuilder;
import com.fluxtion.runtime.EventProcessor;
import com.fluxtion.runtime.partition.LambdaReflection;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

public class CompileTest {
    
    @Test
    public void testCompile() throws Exception {
        boolean compileProcessor = true;

        FluxtionGraphBuilder builder = DynamicCompiler.compileBuilder(
                Path.of("src/test/java/com/fluxtion/server/plugin/eventhandlerloader/MyBuilder.java"),
                System.out::println,
                System.err::println);

        LambdaReflection.SerializableConsumer<EventProcessorConfig> buildGraph = builder::buildGraph;
        EventProcessor<?> eventProcessor = compileProcessor ? Fluxtion.compile(buildGraph) : Fluxtion.interpret(buildGraph);

        eventProcessor.init();
        eventProcessor.onEvent("hello");
    }

    @Test
    public void testBuildEventProcessor() throws Exception {
        EventProcessor<?> eventProcessor = DynamicCompiler.loadEventProcessor(
                true,
                Path.of("src/test/java/com/fluxtion/server/plugin/eventhandlerloader/MyBuilder.java"),
                System.out::println,
                System.err::println);

        eventProcessor.init();
        eventProcessor.onEvent("hello");
    }

}
