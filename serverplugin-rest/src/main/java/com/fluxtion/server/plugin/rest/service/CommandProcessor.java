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
