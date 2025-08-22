package com.fluxtion.server.plugin.trading.component.quickfixj;

import com.fluxtion.server.plugin.trading.component.tradevenue.AbstractOrderExecutor;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import quickfix.*;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Log4j2
public abstract class QuickFixOrderVenue extends AbstractOrderExecutor implements Application {

    @Getter
    @Setter
    private String config;
    private Connector connector;
    @Getter
    @Setter
    private boolean localFile = false;
    protected SessionID session;
    @Getter
    private boolean loggedOn;

    public QuickFixOrderVenue(String config) {
        this.config = config;
    }

    public QuickFixOrderVenue() {
    }

    @SneakyThrows
    @Override
    public void init() {
        super.init();
        log.info("init use local file:{}", localFile);
        if (localFile) {
            connector = createInititatorConnector(
                    this,
                    new FileInputStream(config));
        } else {
            connector = createInititatorConnector(
                    this,
                    QuickFixOrderVenue.class.getClassLoader().getResourceAsStream(config));
        }

    }

    @SneakyThrows
    @Override
    public void start() {
        super.start();
        log.info("start");
        loggedOn = false;
        connector.start();
    }

    @Override
    public void onCreate(SessionID sessionId) {
        log.info("onCreate");
    }

    @Override
    public void onLogon(SessionID sessionId) {
        log.info("onLogon:{}", sessionId);
        this.session = sessionId;
        loggedOn = true;
    }

    @Override
    public void onLogout(SessionID sessionId) {
        log.info("onLogout:{}", sessionId);
        loggedOn = false;
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

    protected void sendToTarget(Message message) throws SessionNotFound {
        Session.sendToTarget(message, session);
    }

    protected void fixLogout(List<String> args, Consumer<String> out, Consumer<String> err) {
        log.info("logout {}", args);
        connector.stop(true);
    }

    protected void fixLogin(List<String> args, Consumer<String> out, Consumer<String> err) {
        try {
            connector.start();
        } catch (ConfigError e) {
            err.accept("login error:" + e.getMessage());
        }
    }

    protected void currentOrders(List<String> strings, Consumer<String> out, Consumer<String> err) {
        out.accept(orderStateManager.getClOrderIdToOrderMap().values().stream()
                .map(Objects::toString)
                .collect(Collectors.joining("\n", getFeedName() + " current orders:\n", "\n----")));
    }

    protected void loggedInStatus(List<String> strings, Consumer<String> out, Consumer<String> err) {
        out.accept(getFeedName() + " logged on:" + isLoggedOn());
    }

    private static Connector createInititatorConnector(Application fixApp, InputStream initiatorConfig) throws ConfigError {
        SessionSettings settings = new SessionSettings(initiatorConfig);
        MessageStoreFactory storeFactory = new FileStoreFactory(settings);

        String logType = null;
        try {
            logType = settings.getString("LogType");
        } catch (ConfigError e) {
            log.warn("Failed to get LogType from settings", e);
        }
        LogFactory logFactory;
        if (logType != null && logType.equalsIgnoreCase("SLF4J")) {
            log.info("LogFactory:SLF4JLogFactory logType:{}", logType);
            logFactory = new SLF4JLogFactory(settings);
        } else {
            log.info("LogFactory:FileLogFactory logType:{}", logType);
            logFactory = new FileLogFactory(settings);
        }

        MessageFactory messageFactory = new DefaultMessageFactory();
        return new SocketInitiator(fixApp, storeFactory, settings, logFactory, messageFactory);
    }
}
