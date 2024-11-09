/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl;


import com.fluxtion.server.lib.pnl.calculator.DerivedRateNode;
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

    public PnlCalculator priceUpdate(MidPrice... midPrices) {
        for (MidPrice midPrice : midPrices) {
            fluxtionPnlCalculator.onEvent(midPrice);
        }
        fluxtionPnlCalculator.publishSignal("positionUpdate");
        return this;
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

    public PnlCalculator addAggregateMtMListener(Consumer<NetMarkToMarket> mtMConsumer) {
        fluxtionPnlCalculator.addSink("globalNetMtmListener", mtMConsumer);
        return this;
    }

    public PnlCalculator addInstrumentMtMListener(Consumer<Map<Instrument, NetMarkToMarket>> mtMConsumer) {
        fluxtionPnlCalculator.addSink("instrumentNetMtmListener", mtMConsumer);
        return this;
    }

    @SneakyThrows
    public NetMarkToMarket aggregateMtm() {
        return fluxtionPnlCalculator.getStreamed("globalNetMtm");
    }

    @SneakyThrows
    public Map<Instrument, NetMarkToMarket> instrumentMtmMap() {
        return fluxtionPnlCalculator.getStreamed("instrumentNetMtm");
    }

    @SneakyThrows
    public Double getRateToMtmBase(Instrument instrument) {
        DerivedRateNode derivedRateNode = fluxtionPnlCalculator.getNodeById("derivedRateNode");
        return derivedRateNode.getRateForInstrument(instrument);
    }

    @SneakyThrows
    public Instrument getMtmBaseInstrument() {
        DerivedRateNode derivedRateNode = fluxtionPnlCalculator.getNodeById("derivedRateNode");
        return derivedRateNode.getMtmInstrument();
    }

    @SneakyThrows
    public Double tradeFees() {
        return aggregateMtm().fees();
    }

    @SneakyThrows
    public Double pnl() {
        return aggregateMtm().tradePnl();
    }

    @SneakyThrows
    public Double netPnl() {
        return aggregateMtm().pnlNetFees();
    }
}
