package com.fluxtion.server.plugin.trading.component.quickfixj;

import com.fluxtion.runtime.lifecycle.Lifecycle;
import com.fluxtion.server.dispatch.EventFlowManager;
import com.fluxtion.server.service.EventFlowService;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import quickfix.*;

import java.io.FileInputStream;
import java.io.InputStream;

@Log4j2
public class QuickFixAcceptor
        extends ApplicationAdapter
        implements
        EventFlowService,
        Lifecycle {

    private final String config;
    private final boolean localFile;
    @Getter(AccessLevel.PACKAGE)
    private Connector connector;
    @Getter(AccessLevel.PACKAGE)
    private EventFlowManager eventFlowManager;
    @Getter(AccessLevel.PACKAGE)
    private String serviceName;

    public QuickFixAcceptor(String config) {
        this(config, false);
    }

    public QuickFixAcceptor(String config, boolean localFile) {
        this.config = config;
        this.localFile = localFile;
    }

    @Override
    public void setEventFlowManager(EventFlowManager eventFlowManager, String serviceName) {
        this.eventFlowManager = eventFlowManager;
        this.serviceName = serviceName;
    }

    @SneakyThrows
    @Override
    public void init() {
        log.info("init");
        if (localFile) {
            connector = createAcceptorConnector(
                    this,
                    new FileInputStream(config));
        } else {
            connector = createAcceptorConnector(
                    this,
                    QuickFixAcceptor.class.getClassLoader().getResourceAsStream(config));
        }
    }

    @SneakyThrows
    @Override
    public void start() {
        log.info("start");
        connector.start();
    }

    @Override
    public void onLogon(SessionID sessionId) {
        log.info("onLogon:{}", sessionId);
    }

    @Override
    public void onLogout(SessionID sessionId) {
        log.info("onLogout:{}", sessionId);
    }

    @Override
    public void toAdmin(Message message, SessionID sessionId) {
        log.debug("toAdmin message:{} sessionId:{}", message, sessionId);
    }

    @Override
    public void toApp(Message message, SessionID sessionId) throws DoNotSend {
        log.debug("toApp message:{} sessionId:{}", message, sessionId);
    }

    @Override
    public void fromAdmin(Message message, SessionID sessionId) throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, RejectLogon {
        log.debug("fromAdmin message:{} sessionId:{}", message, sessionId);
    }

    @Override
    public void fromApp(Message message, SessionID sessionId) throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
        log.debug("fromApp message:{} sessionId:{}", message, sessionId);
    }

    @Override
    public void stop() {
        log.info("stop");
        Lifecycle.super.stop();
    }

    @Override
    public void tearDown() {
        log.info("tearDown");
    }

    private static Connector createAcceptorConnector(Application fixApp, InputStream acceptorConfig) throws ConfigError {

        SessionSettings settings = new SessionSettings(acceptorConfig);
        MessageStoreFactory storeFactory = new FileStoreFactory(settings);

        LogFactory logFactory = new FileLogFactory(settings);
        MessageFactory messageFactory = new DefaultMessageFactory();
        return new SocketAcceptor(fixApp, storeFactory, settings, logFactory, messageFactory);
    }
}
