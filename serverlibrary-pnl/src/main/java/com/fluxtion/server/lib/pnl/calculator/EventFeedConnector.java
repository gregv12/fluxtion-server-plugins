/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl.calculator;

import com.fluxtion.runtime.EventProcessorContext;
import com.fluxtion.runtime.EventProcessorContextListener;
import com.fluxtion.runtime.annotations.ExportService;
import com.fluxtion.runtime.annotations.Initialise;
import com.fluxtion.runtime.annotations.OnEventHandler;
import com.fluxtion.runtime.annotations.Start;
import com.fluxtion.runtime.annotations.builder.FluxtionIgnore;
import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.runtime.audit.EventLogNode;
import com.fluxtion.runtime.node.NamedFeedTableNode;
import com.fluxtion.server.config.ConfigListener;
import com.fluxtion.server.config.ConfigMap;
import com.fluxtion.server.lib.pnl.MidPrice;
import com.fluxtion.server.lib.pnl.PnlCalculator;
import com.fluxtion.server.lib.pnl.Trade;
import com.fluxtion.server.lib.pnl.dto.*;
import com.fluxtion.server.lib.pnl.refdata.InMemorySymbolLookup;
import com.fluxtion.server.lib.pnl.refdata.Instrument;
import com.fluxtion.server.lib.pnl.refdata.Symbol;
import com.fluxtion.server.service.admin.AdminCommandRegistry;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.function.Consumer;

@Log4j2
public class EventFeedConnector extends EventLogNode implements EventProcessorContextListener, @ExportService ConfigListener {

    @FluxtionIgnore
    private final InMemorySymbolLookup symbolLookup = new InMemorySymbolLookup();
    private final NamedFeedTableNode<String, SymbolDto> symbolTable;
    private EventProcessorContext context;
    private Logger erroLogger = LogManager.getLogger("com.fluxtion.pnl.error");

    public EventFeedConnector() {
        this(new NamedFeedTableNode<>(
                "symbolFeed",
                "com.fluxtion.server.lib.pnl.dto.SymbolDto::getSymbol"));
    }

    public EventFeedConnector(NamedFeedTableNode<String, SymbolDto> symbolTable) {
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
        publishSignal(PnlCalculator.POSITION_SNAPSHOT_RESET);
        publishSignal(PnlCalculator.POSITION_UPDATE_EOB);
    }

    @Override
    public boolean initialConfig(ConfigMap config) {
        erroLogger = LogManager.getLogger(config.getOrDefault("errorLogName", "com.fluxtion.pnl.error"));
        return false;
    }

    @Start
    public void start() {
        log.info("Starting EventFeedConnector symbolMap: {}", symbolTable.getTableMap());
    }

    @OnEventHandler
    public boolean onTradeDto(TradeDto tradeDTO) {
        log.info("Received TradeDTO: " + tradeDTO);
        processTradeDto(tradeDTO);
        return false;
    }

    @OnEventHandler
    public boolean onTradeBatchDto(TradeBatchDto tradeBatchDTO) {
        log.info("Received TradeBatchDTO: " + tradeBatchDTO);
        for (TradeDto tradeDTO : tradeBatchDTO.getBatchData()) {
            processTradeDto(tradeDTO);
        }
        return false;
    }

    @OnEventHandler
    public boolean onMidPriceDto(MidPriceDto midPriceDto) {
        log.info("Received midPriceDto: " + midPriceDto);
        MidPrice midPrice = new MidPrice(getSymbolForName(midPriceDto.getSymbol()), midPriceDto.getPrice());
        onEvent(midPrice);
        publishSignal(PnlCalculator.POSITION_UPDATE_EOB);
        return false;
    }

    @OnEventHandler
    public boolean onMidPriceBatchDto(MidPriceBatchDto midPriceBatchDto) {
        log.info("Received midPriceBatchDto: " + midPriceBatchDto);
        midPriceBatchDto.getBatchData().stream()
                .map(midPriceDto -> new MidPrice(getSymbolForName(midPriceDto.getSymbol()), midPriceDto.getPrice()))
                .forEach(context.getStaticEventProcessor()::onEvent);
        publishSignal(PnlCalculator.POSITION_UPDATE_EOB);
        return false;
    }

    @OnEventHandler
    public boolean onPositionSnapshot(PositionSnapshotDto positionSnapshot) {
        log.info("Received positionSnapshot: " + positionSnapshot);
        publishSignal(PnlCalculator.POSITION_SNAPSHOT_RESET);
        onEvent(positionSnapshot.getPositionSnapshot());
        publishSignal(PnlCalculator.POSITION_UPDATE_EOB);
        return true;
    }

    private void resetPosition(List<String> strings, Consumer<String> out, Consumer<String> err) {
        log.info("Received resetPosition command");
        publishSignal(PnlCalculator.POSITION_SNAPSHOT_RESET);
        publishSignal(PnlCalculator.POSITION_UPDATE_EOB);
        (out).accept("resetPosition");
    }

    private void processTradeDto(TradeDto tradeDTO) {
        Symbol symbolForName = getSymbolForName(tradeDTO.getSymbol());
        if (symbolForName != null) {
            String feeInstrument = tradeDTO.getFeeInstrument() == null ? "" : tradeDTO.getFeeInstrument();
            Trade trade = new Trade(
                    symbolForName,
                    tradeDTO.getDealtVolume(),
                    tradeDTO.getContraVolume(),
                    tradeDTO.getFee(),
                    feeInstrument.isBlank() ? Instrument.INSTRUMENT_USD : new Instrument(feeInstrument));
            onEvent(trade);
            publishSignal(PnlCalculator.POSITION_UPDATE_EOB);
        } else {
            erroLogger.error("trade processing problem missing symbol:{} for:{}", tradeDTO.getSymbol(), tradeDTO);
        }
    }

    private void publishSignal(String signal) {
        context.getStaticEventProcessor().publishSignal(signal);
    }

    private void onEvent(Object event) {
        context.getStaticEventProcessor().onEvent(event);
    }

    private Symbol getSymbolForName(String name) {
        Symbol symbol = symbolLookup.getSymbolForName(name);
        if (symbol == null) {
            symbol = symbolTable.getTableMap().get(name).toSymbol();
            symbolLookup.addSymbol(symbol);
        }
        return symbol;
    }
}
