package com.fluxtion.server.plugin.connector.chronicle;

/**
 * A simple sink for publishing events/messages to an output.
 *
 * @param <T> the type of event this sink handles
 */
@FunctionalInterface
public interface MessageSink<T> {

    /**
     * Publish an event to the sink.
     *
     * @param event the event to publish
     */
    void onEvent(T event);
}
