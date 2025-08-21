package com.fluxtion.server.plugin.trading.component.quickfixj;

import com.fluxtion.server.plugin.trading.component.marketdatafeed.AbstractMarketDataFeed;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import quickfix.*;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.function.Consumer;

@Log4j2
public class QuickFixMarketDataFeed extends AbstractMarketDataFeed implements Application {

    @Getter
    @Setter
    private String config;
    private Connector connector;
    protected SessionID session;
    @Getter
    private boolean loggedOn;
    @Getter
    @Setter
    private boolean localFile = false;

    public QuickFixMarketDataFeed() {
    }

    public QuickFixMarketDataFeed(String config) {
        this.config = config;
    }

    public QuickFixMarketDataFeed(String config, boolean localFile) {
        this.config = config;
        this.localFile = localFile;
    }

    @Override
    protected void subscribeToSymbol(String feedName, String venueName, String symbol) {
        log.info("service:{} subscribeToSymbol:{} venueName:{} feedName:{}", getServiceName(), symbol, venueName, feedName);
        subscriptions.add(symbol);
    }

    @SneakyThrows
    @Override
    public void init() {
        log.info("init use local file:{}", localFile);
        if (localFile) {
            connector = createInititatorConnector(
                    this,
                    new FileInputStream(config));
        } else {
            connector = createInititatorConnector(
                    this,
                    QuickFixMarketDataFeed.class.getClassLoader().getResourceAsStream(config));
        }

    }

    @SneakyThrows
    @Override
    public void start() {
        super.start();
        log.info("start");
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

    protected void fixLogout(List<String> args, Consumer<String> out, Consumer<String> err) {
        log.info("logout {}", args);
        connector.stop();
    }

    protected void fixLogin(List<String> args, Consumer<String> out, Consumer<String> err) {
        try {
            connector.start();
        } catch (ConfigError e) {
            err.accept("login error:" + e.getMessage());
        }
    }

    private static Connector createInititatorConnector(Application fixApp, InputStream initiatorConfig) throws ConfigError {

        SessionSettings settings = new SessionSettings(initiatorConfig);
        MessageStoreFactory storeFactory = new FileStoreFactory(settings);

        String logType = settings.getString("LogType");
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
