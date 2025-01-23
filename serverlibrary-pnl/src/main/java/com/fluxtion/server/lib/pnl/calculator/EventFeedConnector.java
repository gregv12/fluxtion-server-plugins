/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl.calculator;

import com.fluxtion.runtime.EventProcessorContext;
import com.fluxtion.runtime.EventProcessorContextListener;
import com.fluxtion.runtime.annotations.ExportService;
import com.fluxtion.runtime.annotations.Initialise;
import com.fluxtion.runtime.annotations.Start;
import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.runtime.audit.EventLogNode;
import com.fluxtion.runtime.node.NamedFeedTableNode;
import com.fluxtion.server.config.ConfigListener;
import com.fluxtion.server.config.ConfigMap;
import com.fluxtion.server.lib.pnl.PnlCalculator;
import com.fluxtion.server.lib.pnl.Trade;
import com.fluxtion.server.lib.pnl.refdata.Symbol;
import com.fluxtion.server.service.admin.AdminCommandRegistry;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.function.Consumer;

@Log4j2
public class EventFeedConnector extends EventLogNode implements EventProcessorContextListener, @ExportService ConfigListener {

    private final NamedFeedTableNode<String, Symbol> symbolTable;
    private EventProcessorContext context;
    private Logger erroLogger = LogManager.getLogger("com.fluxtion.pnl.error");

    public EventFeedConnector(NamedFeedTableNode<String, Symbol> symbolTable) {
        this.symbolTable = symbolTable;
    }

    @Override
    public void currentContext(EventProcessorContext currentContext) {
        this.context = currentContext;
    }

    @ServiceRegistered
    public void admin(AdminCommandRegistry registry) {
        registry.registerCommand("resetPosition", this::resetPosition);
    }

    @Initialise
    public void init() {
        log.info("Initialising EventFeedConnector");
        auditLog.info("lifecycle", "init");
        publishSignal(PnlCalculator.POSITION_SNAPSHOT_RESET);
    }

    @Override
    public boolean initialConfig(ConfigMap config) {
        erroLogger = LogManager.getLogger(config.getOrDefault("errorLogName", "com.fluxtion.pnl.error"));
        return false;
    }

    @Start
    public void start() {
        log.info("Starting EventFeedConnector symbolMap: {}", symbolTable.getTableMap());
        publishSignal(PnlCalculator.POSITION_UPDATE_EOB);
    }

    public Trade validateTrade(Trade trade) {
        try {
            if (validateBatchTrade(trade) == null) {
                return null;
            }
            return trade;
        } catch (Exception e) {
            auditLog.warn("tradeValidation", "failed")
                    .warn("trade", trade)
                    .warn("error", e);
            erroLogger.error("trade processing problem missing symbol:{} for:{}", trade.getSymbolName(), trade);
        }
        return null;
    }

    public Trade validateBatchTrade(Trade trade) {
        try {
            if (trade.getSymbol() == null) {
                trade.setSymbol(symbolTable.getTableMap().get(trade.getSymbolName()));
            }
            return trade;
        } catch (Exception e) {
            auditLog.warn("tradeValidation", "failed")
                    .warn("trade", trade)
                    .warn("error", e);
            erroLogger.error("trade processing problem missing symbol:{} for:{}", trade.getSymbolName(), trade);
        }
        return null;
    }

    private void resetPosition(List<String> strings, Consumer<String> out, Consumer<String> err) {
        log.info("Received resetPosition command");
        publishSignal(PnlCalculator.POSITION_SNAPSHOT_RESET);
        publishSignal(PnlCalculator.POSITION_UPDATE_EOB);
        out.accept("resetPosition");
    }

    private void publishSignal(String signal) {
        context.getStaticEventProcessor().publishSignal(signal);
    }
}
