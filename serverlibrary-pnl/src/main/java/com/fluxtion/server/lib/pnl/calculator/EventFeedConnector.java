/*
 *
 *  * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 *  * SPDX-License-Identifier: AGPL-3.0-only
 *  *
 *  
 */

package com.fluxtion.server.lib.pnl.calculator;

import com.fluxtion.runtime.EventProcessorContext;
import com.fluxtion.runtime.EventProcessorContextListener;
import com.fluxtion.runtime.annotations.Initialise;
import com.fluxtion.runtime.annotations.OnEventHandler;
import com.fluxtion.runtime.annotations.Start;
import com.fluxtion.runtime.annotations.builder.FluxtionIgnore;
import com.fluxtion.runtime.event.NamedFeedEvent;
import com.fluxtion.server.lib.pnl.Trade;
import com.fluxtion.server.lib.pnl.refdata.InMemorySymbolLookup;
import com.fluxtion.server.lib.pnl.refdata.Instrument;
import com.fluxtion.server.lib.pnl.refdata.Symbol;
import lombok.extern.log4j.Log4j2;

import static com.fluxtion.server.lib.pnl.PnlCalculator.POSITION_SNAPSHOT_RESET;
import static com.fluxtion.server.lib.pnl.PnlCalculator.POSITION_UPDATE_EOB;

@Log4j2
public class EventFeedConnector implements EventProcessorContextListener {

    public static final String TRADE_EVENT_FEED = "tradeEventFeed";
    @FluxtionIgnore
    private final InMemorySymbolLookup symbolLookup = new InMemorySymbolLookup();
    private EventProcessorContext context;

    public static final Instrument EUR = new Instrument("EUR");
    public static final Instrument USD = new Instrument("USD");
    public static final Instrument USDT = new Instrument("USDT");
    public static final Instrument MXN = new Instrument("MXN");
    public static final Instrument CHF = new Instrument("CHF");
    public static final Instrument JPY = new Instrument("JPY");
    public static final Instrument GBP = new Instrument("GBP");
    public static final Instrument BTC = new Instrument("BTC");
    public final static Symbol symbolEURUSD = new Symbol("EURUSD", EUR, USD);
    public final static Symbol symbolEURCHF = new Symbol("EURCHF", EUR, CHF);
    public final static Symbol symbolUSDCHF = new Symbol("USDCHF", USD, CHF);
    public final static Symbol symbolCHFUSD = new Symbol("CHFUSD", CHF, USD);
    public final static Symbol symbolEURJPY = new Symbol("EURJPY", EUR, JPY);
    public final static Symbol symbolUSDJPY = new Symbol("USDJPY", USD, JPY);
//    public final static Symbol symbolGBPUSD = new Symbol("GBPUSD", GBP, USD);
//    public final static Symbol symbolEURGBP = new Symbol("EURGBP", EUR, GBP);
//    public final static Symbol symbolUSDTMXN = new Symbol("USDTMXN", USDT, MXN);
//    public final static Symbol symbolMXNUSDT = new Symbol("MXNUSDT", MXN, USDT);
//    public final static Symbol symbolUSDMXN = new Symbol("USDMXN", USD, MXN);
//    public final static Symbol symbolUSDUSDT = new Symbol("USDUSDT", USD, USDT);
//    public final static Symbol symbolBTCEUR = new Symbol("BTCEUR", BTC, EUR);
//    public final static Symbol symbolBTCUSD = new Symbol("BTCUSD", BTC, USD);

    @Override
    public void currentContext(EventProcessorContext currentContext) {
        this.context = currentContext;
        currentContext.subscribeToNamedFeed(TRADE_EVENT_FEED);
//        currentContext.getSubscriptionManager().subscribeToNamedFeed(
//                new EventSubscription<>(TRADE_EVENT_FEED, Integer.MAX_VALUE, TRADE_EVENT_FEED, NamedFeedEvent.class)
//        );
    }

    @Initialise
    public void init() {
        symbolLookup.addSymbol(symbolEURUSD);
        symbolLookup.addSymbol(symbolEURCHF);
        symbolLookup.addSymbol(symbolUSDCHF);
        symbolLookup.addSymbol(symbolCHFUSD);
        symbolLookup.addSymbol(symbolEURJPY);
        symbolLookup.addSymbol(symbolUSDJPY);
    }

    @Start
    public void start() {
        context.getStaticEventProcessor().publishSignal(POSITION_SNAPSHOT_RESET);
        context.getStaticEventProcessor().publishSignal(POSITION_UPDATE_EOB);
    }

    @OnEventHandler(filterString = TRADE_EVENT_FEED)
    public boolean onEvent(NamedFeedEvent<String> event) {
        log.info("Received TradeEvent: " + event);
        Trade trade = new Trade(symbolEURUSD, 200, 220, 0);
        context.getStaticEventProcessor().onEvent(trade);
        context.getStaticEventProcessor().publishSignal(POSITION_UPDATE_EOB);
        return false;
    }
}
