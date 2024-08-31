/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.rest.service;

import lombok.Getter;

import java.util.function.Consumer;

public abstract class CommandProcessor<S, T> {

    @Getter
    private final Class<S> argumentClass;

    protected CommandProcessor(Class<S> argumentClass) {
        this.argumentClass = argumentClass;
    }

    abstract void process(S command, Consumer<T> outputConsumer);
}
