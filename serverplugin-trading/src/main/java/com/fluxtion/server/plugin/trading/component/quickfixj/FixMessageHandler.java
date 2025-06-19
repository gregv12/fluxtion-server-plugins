package com.fluxtion.server.plugin.trading.component.quickfixj;

import quickfix.*;

public interface FixMessageHandler {

    void processFixMessage(Message message, SessionID sessionId) throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType;
}
