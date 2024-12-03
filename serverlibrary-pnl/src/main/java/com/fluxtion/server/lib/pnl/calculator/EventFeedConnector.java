/*
 *
 *  * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 *  * SPDX-License-Identifier: AGPL-3.0-only
 *
 */

package com.fluxtion.server.lib.pnl.calculator;

import com.fluxtion.runtime.EventProcessorContext;
import com.fluxtion.runtime.EventProcessorContextListener;
import com.fluxtion.runtime.annotations.ExportService;
import com.fluxtion.runtime.annotations.Initialise;
import com.fluxtion.runtime.annotations.OnEventHandler;
import com.fluxtion.runtime.annotations.Start;
import com.fluxtion.runtime.annotations.builder.FluxtionIgnore;
import com.fluxtion.runtime.event.NamedFeedEvent;
import com.fluxtion.server.config.ConfigListener;
import com.fluxtion.server.config.ConfigMap;
import com.fluxtion.server.lib.pnl.PnlCalculator;
import com.fluxtion.server.lib.pnl.Trade;
import com.fluxtion.server.lib.pnl.TradeBatchDTO;
import com.fluxtion.server.lib.pnl.TradeDTO;
import com.fluxtion.server.lib.pnl.refdata.InMemorySymbolLookup;
import com.fluxtion.server.lib.pnl.refdata.Instrument;
import com.fluxtion.server.lib.pnl.refdata.Symbol;
import lombok.extern.log4j.Log4j2;

import java.util.List;
import java.util.Map;

@Log4j2
public class EventFeedConnector implements EventProcessorContextListener, @ExportService ConfigListener {

    public static final String TRADE_EVENT_FEED = "tradeEventFeed";
    @FluxtionIgnore
    private final InMemorySymbolLookup symbolLookup = new InMemorySymbolLookup();
    private EventProcessorContext context;

    @Override
    public void currentContext(EventProcessorContext currentContext) {
        this.context = currentContext;
        currentContext.subscribeToNamedFeed(TRADE_EVENT_FEED);
    }

    @Initialise
    public void init() {
        log.info("Initialising EventFeedConnector");
    }

    @Override
    public boolean initialConfig(ConfigMap config) {
        List<Map<String, String>> symbolList = config.get("symbols");
        symbolList.stream()
                .map(cfg -> new Symbol(cfg.get("symbol"), cfg.get("dealt"), cfg.get("contra")))
                .forEach(symbolLookup::addSymbol);
        return false;
    }

    @Start
    public void start() {
        log.info("Starting EventFeedConnector - reset calculator");
        context.getStaticEventProcessor().publishSignal(PnlCalculator.POSITION_SNAPSHOT_RESET);
        context.getStaticEventProcessor().publishSignal(PnlCalculator.POSITION_UPDATE_EOB);
    }

    @OnEventHandler(filterString = TRADE_EVENT_FEED)
    public boolean onEvent(NamedFeedEvent<Trade> event) {
        log.info("Received TradeEvent: " + event);
        Trade trade = event.getData();
        context.getStaticEventProcessor().onEvent(trade);
        context.getStaticEventProcessor().publishSignal(PnlCalculator.POSITION_UPDATE_EOB);
        return false;
    }

    @OnEventHandler
    public boolean onEvent(TradeDTO tradeDTO) {
        log.info("Received TradeDTO: " + tradeDTO);
        String feeInstrument = tradeDTO.getFeeInstrument() == null ? "" : tradeDTO.getFeeInstrument();
        Trade trade = new Trade(
                symbolLookup.getSymbolForName(tradeDTO.getSymbol()),
                tradeDTO.getDealtVolume(),
                tradeDTO.getContraVolume(),
                tradeDTO.getFee(),
                feeInstrument.isBlank() ? Instrument.INSTRUMENT_USD : new Instrument(feeInstrument));
        context.getStaticEventProcessor().onEvent(trade);
        context.getStaticEventProcessor().publishSignal(PnlCalculator.POSITION_UPDATE_EOB);
        return false;
    }

    @OnEventHandler
    public boolean onEvent(TradeBatchDTO tradeBatchDTO) {
        log.info("Received TradeBatchDTO: " + tradeBatchDTO);
        for (TradeDTO tradeDTO : tradeBatchDTO.getTrades()) {
            String feeInstrument = tradeDTO.getFeeInstrument() == null ? "" : tradeDTO.getFeeInstrument();
            Trade trade = new Trade(
                    symbolLookup.getSymbolForName(tradeDTO.getSymbol()),
                    tradeDTO.getDealtVolume(),
                    tradeDTO.getContraVolume(),
                    tradeDTO.getFee(),
                    feeInstrument.isBlank() ? Instrument.INSTRUMENT_USD : new Instrument(feeInstrument));
            context.getStaticEventProcessor().onEvent(trade);
        }

        context.getStaticEventProcessor().publishSignal(PnlCalculator.POSITION_UPDATE_EOB);
        return false;
    }
}
