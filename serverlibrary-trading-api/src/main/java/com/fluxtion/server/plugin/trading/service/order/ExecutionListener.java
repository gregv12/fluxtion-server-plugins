package com.fluxtion.server.plugin.trading.service.order;

import java.util.function.Consumer;

public interface ExecutionListener {

    default boolean replayMode() {
        return false;
    }

    default boolean liveMode() {
        return false;
    }

    default boolean replayStarted() {
        return false;
    }

    default boolean replayComplete(Consumer<String> processCompletedCallback) {
        return false;
    }

    boolean orderFill(OrderFillEvent orderFillEvent);

    boolean orderFilled(OrderFilledEvent orderFilledEvent);
}
