/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl;


import com.fluxtion.server.lib.pnl.calculator.FluxtionPnlCalculator;
import com.fluxtion.server.lib.pnl.refdata.InMemorySymbolLookup;
import com.fluxtion.server.lib.pnl.refdata.Instrument;
import com.fluxtion.server.lib.pnl.refdata.Symbol;
import lombok.Getter;
import lombok.SneakyThrows;

import java.util.Collection;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public class PnlCalculator {

    private final FluxtionPnlCalculator fluxtionPnlCalculator;
    @Getter
    private final InMemorySymbolLookup symbolLookup = new InMemorySymbolLookup();

    public PnlCalculator() {
        fluxtionPnlCalculator = new FluxtionPnlCalculator();
        fluxtionPnlCalculator.init();
        fluxtionPnlCalculator.onEvent(symbolLookup);
        fluxtionPnlCalculator.start();
        positionReset();
    }

    public PnlCalculator setMtmInstrument(Instrument instrument) {
        fluxtionPnlCalculator.onEvent(new MtmInstrument(instrument));
        fluxtionPnlCalculator.publishSignal("positionUpdate");
        return this;
    }

    public PnlCalculator addSymbol(Symbol symbol) {
        symbolLookup.addSymbol(symbol);
        return this;
    }

    public PnlCalculator positionSnapshot(PositionSnapshot positionSnapshot) {
        fluxtionPnlCalculator.onEvent(positionSnapshot);
        fluxtionPnlCalculator.publishSignal("positionUpdate");
        return this;
    }

    public PnlCalculator positionReset(PositionSnapshot positionSnapshot) {
        fluxtionPnlCalculator.publishSignal("positionSnapshotReset");
        fluxtionPnlCalculator.onEvent(positionSnapshot);
        fluxtionPnlCalculator.publishSignal("positionUpdate");
        return this;
    }

    public PnlCalculator positionReset() {
        fluxtionPnlCalculator.publishSignal("positionSnapshotReset");
        fluxtionPnlCalculator.publishSignal("positionUpdate");
        return this;
    }

    public PnlCalculator priceUpdate(MidPrice midPrice) {
        fluxtionPnlCalculator.onEvent(midPrice);
        fluxtionPnlCalculator.publishSignal("positionUpdate");
        return this;
    }

    public PnlCalculator priceUpdate(String symbolName, double price) {
        Symbol symbol = symbolLookup.getSymbolForName(symbolName);
        return symbol == null ? this : priceUpdate(new MidPrice(symbol, price));
    }

    public PnlCalculator processTrade(Trade... trades) {
        for (Trade trade : trades) {
            fluxtionPnlCalculator.onEvent(trade);
        }
        fluxtionPnlCalculator.publishSignal("positionUpdate");
        return this;
    }

    public PnlCalculator processTradeBatch(Collection<Trade> trades) {
        for (Trade trade : trades) {
            fluxtionPnlCalculator.onEvent(trade);
        }
        fluxtionPnlCalculator.publishSignal("positionUpdate");
        return this;
    }

    public PnlCalculator processTradeBatch(TradeBatch trades) {
        fluxtionPnlCalculator.onEvent(trades);
        fluxtionPnlCalculator.publishSignal("positionUpdate");
        return this;
    }

    public <T> PnlCalculator processCustomTrade(T trade, Function<T, TradeBatch> tradeToBatchFunction) {
        return processTradeBatch(tradeToBatchFunction.apply(trade));
    }

    public PnlCalculator addPnlListener(Consumer<Double> pnlConsumer) {
        fluxtionPnlCalculator.addSink("pnlListener", pnlConsumer);
        return this;
    }

    public PnlCalculator addNetPnlListener(Consumer<Double> pnlConsumer) {
        fluxtionPnlCalculator.addSink("netPnlListener", pnlConsumer);
        return this;
    }

    public PnlCalculator addTradeFeesListener(Consumer<Double> pnlConsumer) {
        fluxtionPnlCalculator.addSink("tradeFeesListener", pnlConsumer);
        return this;
    }

    public PnlCalculator addTradeFeesPositionMapListener(Consumer<Map<String, Double>> pnlConsumer) {
        fluxtionPnlCalculator.addSink("feePositionListener", pnlConsumer);
        return this;
    }

    public PnlCalculator addTradeFeesMtmPositionMapListener(Consumer<Map<String, Double>> pnlConsumer) {
        fluxtionPnlCalculator.addSink("mtmFeePositionListener", pnlConsumer);
        return this;
    }

    public PnlCalculator addPositionListener(Consumer<Map<String, Double>> positionListener) {
        fluxtionPnlCalculator.addSink("positionListener", positionListener);
        return this;
    }

    public PnlCalculator addMtmPositionListener(Consumer<Map<String, Double>> positionListener) {
        fluxtionPnlCalculator.addSink("mtmPositionListener", positionListener);
        return this;
    }

    public PnlCalculator addRateListener(Consumer<Map<String, Double>> rateListener) {
        fluxtionPnlCalculator.addSink("rateListener", rateListener);
        return this;
    }

    @SneakyThrows
    public Map<String, Double> positionMap() {
        return fluxtionPnlCalculator.getStreamed("positionMap");
    }

    @SneakyThrows
    public Map<String, Double> mtmPositionMap() {
        return fluxtionPnlCalculator.getStreamed("mtmPositionMap");
    }

    @SneakyThrows
    public Map<String, Double> ratesMap() {
        return fluxtionPnlCalculator.getStreamed("rates");
    }

    @SneakyThrows
    public Double tradeFees() {
        return fluxtionPnlCalculator.getStreamed("tradeFees");
    }

    @SneakyThrows
    public Double pnl() {
        return fluxtionPnlCalculator.getStreamed("pnl");
    }

    @SneakyThrows
    public Double netPnl() {
        return fluxtionPnlCalculator.getStreamed("netPnl");
    }
}
