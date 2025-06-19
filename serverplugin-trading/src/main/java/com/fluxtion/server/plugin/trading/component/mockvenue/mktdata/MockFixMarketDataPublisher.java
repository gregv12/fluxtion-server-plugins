package com.fluxtion.server.plugin.trading.component.mockvenue.mktdata;

import com.fluxtion.agrona.concurrent.Agent;
import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.server.plugin.trading.component.quickfixj.QuickFixAcceptor;
import com.fluxtion.server.service.scheduler.SchedulerService;
import lombok.extern.log4j.Log4j2;
import quickfix.*;
import quickfix.field.*;
import quickfix.fix44.MarketDataRequest;
import quickfix.fix44.MarketDataSnapshotFullRefresh;

@Log4j2
public class MockFixMarketDataPublisher extends QuickFixAcceptor implements Agent {

    private SchedulerService schedulerService;
    private String symbol;
    private String requestId;
    private SessionID sessionId;

    public MockFixMarketDataPublisher(String config) {
        super(config);
    }

    public MockFixMarketDataPublisher(String config, boolean localFile) {
        super(config, localFile);
    }

    @ServiceRegistered
    public void scheduler(SchedulerService schedulerService) {
        log.info("schedulerService registered");
        this.schedulerService = schedulerService;
        schedulerService.scheduleAfterDelay(1_000, this::sendTick);
    }

    @Override
    public void fromApp(Message message, SessionID sessionId) throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
        super.fromApp(message, sessionId);
        if (MarketDataRequest.MSGTYPE.equals(message.getHeader().getString(MsgType.FIELD))) {
            requestId = message.getString(MDReqID.FIELD);

            for (Group symbolGroup : message.getGroups(NoRelatedSym.FIELD)) {
                symbol = symbolGroup.getString(Symbol.FIELD);
            }

            this.sessionId = sessionId;
            log.info("subscription - " + symbol);
        }
    }

    @Override
    public void fromAdmin(Message message, SessionID sessionId) throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, RejectLogon {
        super.fromAdmin(message, sessionId);
    }

    private void sendTick() {
        log.debug("sending tick");
        if (requestId != null) {
            MarketDataSnapshotFullRefresh message = new MarketDataSnapshotFullRefresh();
            message.setString(MDReqID.FIELD, requestId);
            message.setString(Symbol.FIELD, symbol);

            MarketDataSnapshotFullRefresh.NoMDEntries group = new MarketDataSnapshotFullRefresh.NoMDEntries();

            group.set(new MDEntryType('0'));
            group.set(new MDEntryPx(11.02 + Math.random()));
            group.set(new MDEntrySize(25 + (int) (Math.random() * 10)));
            message.addGroup(group);

            group.set(new MDEntryType('1'));
            group.set(new MDEntryPx(12.32 + Math.random()));
            group.set(new MDEntrySize(40 + (int) (Math.random() * 10)));
            message.addGroup(group);

            try {
                Session.sendToTarget(message, sessionId);
            } catch (SessionNotFound e) {
                throw new RuntimeException(e);
            }
        }
        schedulerService.scheduleAfterDelay(1_000, this::sendTick);
    }

    @Override
    public int doWork() throws Exception {
        return 0;
    }

    @Override
    public String roleName() {
        return "MockFixMarketDataPublisher";
    }
}
