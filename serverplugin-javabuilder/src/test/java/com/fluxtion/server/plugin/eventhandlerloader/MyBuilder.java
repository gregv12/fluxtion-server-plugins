/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.eventhandlerloader;

import com.fluxtion.compiler.EventProcessorConfig;
import com.fluxtion.compiler.FluxtionCompilerConfig;
import com.fluxtion.compiler.FluxtionGraphBuilder;
import com.fluxtion.runtime.annotations.OnEventHandler;

public class MyBuilder implements FluxtionGraphBuilder {
    @Override
    public void buildGraph(EventProcessorConfig eventProcessorConfig) {
        eventProcessorConfig.addNode(new MyStringHandler());
    }

    @Override
    public void configureGeneration(FluxtionCompilerConfig compilerConfig) {

    }

    public static class MyStringHandler {
        @OnEventHandler
        public boolean stringUpdate(String in) {
            System.out.println(in);
            return true;
        }
    }
}
