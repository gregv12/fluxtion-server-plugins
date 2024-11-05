/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl.calculator;

import com.fluxtion.compiler.EventProcessorConfig;
import com.fluxtion.compiler.FluxtionCompilerConfig;
import com.fluxtion.compiler.FluxtionGraphBuilder;
import com.fluxtion.compiler.builder.dataflow.DataFlow;
import com.fluxtion.compiler.builder.dataflow.FlowBuilder;
import com.fluxtion.compiler.builder.dataflow.GroupByFlowBuilder;
import com.fluxtion.compiler.builder.dataflow.JoinFlowBuilder;
import com.fluxtion.runtime.dataflow.aggregate.function.primitive.DoubleSumFlowFunction;
import com.fluxtion.runtime.dataflow.groupby.GroupBy;
import com.fluxtion.runtime.dataflow.helpers.Mappers;
import com.fluxtion.runtime.event.Signal;
import com.fluxtion.server.lib.pnl.*;
import com.fluxtion.server.lib.pnl.refdata.Instrument;
import lombok.Getter;

/**
 * Builds the {@link FluxtionPnlCalculator} AOT using the Fluxtion maven plugin.
 * <p>
 * This build can be extended with a subclass or used as a utility to build a graph with the streaming nodes available
 * through accessors
 */

@Getter
public class FluxtionPnlCalculatorBuilder implements FluxtionGraphBuilder {

    private EventProcessorConfig eventProcessorConfig;
    protected FlowBuilder<Signal> positionUpdateEob;
    protected FlowBuilder<Signal> positionSnapshotReset;
    protected SymbolLookupNode symbolLookup;
    protected FlowBuilder<Trade> tradeStream;
    protected GroupByFlowBuilder<Instrument, Double> snapshotPositionMap;
    protected GroupByFlowBuilder<Instrument, Double> positionMap;
    protected GroupByFlowBuilder<Instrument, Double> rateMap;
    protected GroupByFlowBuilder<Instrument, Double> mtmPositionMap;
    protected FlowBuilder<Double> feeStream;
    protected FlowBuilder<Double> pnl;
    protected FlowBuilder<Double> netPnl;

    public static void buildLookup(EventProcessorConfig config) {
        FluxtionPnlCalculatorBuilder calculatorBuilder = new FluxtionPnlCalculatorBuilder();
        calculatorBuilder.buildGraph(config);
    }

    @Override
    public void buildGraph(EventProcessorConfig eventProcessorConfig) {
        this.eventProcessorConfig = eventProcessorConfig;
        buildSharedNodes();
        buildTradeStream();
        buildTradeFees();
        buildPositionMap();
        buildRateMap();
        buildMarkToMarket();
        buildSinkOutputs();

        //no buffer/trigger support required on this  processor
        eventProcessorConfig.setSupportBufferAndTrigger(false);
    }

    @Override
    public void configureGeneration(FluxtionCompilerConfig compilerConfig) {
        compilerConfig.setClassName("FluxtionPnlCalculator");
        compilerConfig.setPackageName("com.fluxtion.server.lib.pnl.calculator");
    }

    private void buildSharedNodes() {
        symbolLookup = eventProcessorConfig.addNode(new SymbolLookupNode());
        //signal listeners for batch and reset triggering
        positionUpdateEob = DataFlow.subscribeToSignal("positionUpdate");
        positionSnapshotReset = DataFlow.subscribeToSignal("positionSnapshotReset");
    }

    private void buildTradeStream() {
        var batchTradeStream = DataFlow.subscribe(TradeBatch.class)
                .flatMap(TradeBatch::getTrades);

        tradeStream = DataFlow.subscribe(Trade.class)
                .merge(batchTradeStream);
    }

    private void buildTradeFees() {
        feeStream = tradeStream
                .map(Trade::getFee)
                .merge(DataFlow.subscribe(TradeBatch.class).map(TradeBatch::getFee))
                .aggregate(DoubleSumFlowFunction::new)
                .defaultValue(0.0)
                .resetTrigger(positionSnapshotReset)
                .map(MathUtil::round8dp)
                .publishTriggerOverride(positionUpdateEob);
    }

    private void buildPositionMap() {
        //Asset position map created by PositionSnapshot
        snapshotPositionMap = DataFlow.subscribe(PositionSnapshot.class)
                .flatMap(PositionSnapshot::getPositions)
                .groupBy(InstrumentPosition::instrument, InstrumentPosition::position)
                .resetTrigger(positionSnapshotReset)
                .publishTriggerOverride(positionUpdateEob);

        //create a map of asset positions by instrument, map updates on every Booking request, merge with position snapshot
        positionMap = JoinFlowBuilder.outerJoin(
                        //dealt instrument position
                        tradeStream
                                .groupBy(Trade::getDealtInstrument, Trade::getDealtVolume, DoubleSumFlowFunction::new)
                                .resetTrigger(positionSnapshotReset),
                        //contra instrument position
                        tradeStream
                                .groupBy(Trade::getContraInstrument, Trade::getContraVolume, DoubleSumFlowFunction::new)
                                .resetTrigger(positionSnapshotReset))
                .publishTriggerOverride(positionUpdateEob)
                .mapValues(MathUtil::addPositions)
                //join + add to snapshot map
                .outerJoin(snapshotPositionMap)
                .mapValues(MathUtil::addPositions)
                .defaultValue(GroupBy.emptyCollection())
                .updateTrigger(positionUpdateEob);
    }

    private void buildRateMap() {
        //create a map of asset rates to USD, updates on any rate event
        rateMap = DataFlow.subscribe(MidPrice.class)
                .filter(MidPrice::hasUsdRate)
                .groupBy(MidPrice::getUsdContraInstrument, MidPrice::getUsdRate)
                .mapBiFunction(new DerivedRateNode()::addDerived, positionMap)
                .defaultValue(GroupBy.emptyCollection());
    }

    private void buildMarkToMarket() {
        //mtm positions
        mtmPositionMap = JoinFlowBuilder.leftJoin(positionMap, rateMap)
                .mapValues(MathUtil::mtmPositions)
                .updateTrigger(positionUpdateEob);

        //aggregate pnl calculation triggers a recalc on any change to either rates or positions
        pnl = mtmPositionMap
                .reduceValues(DoubleSumFlowFunction::new)
                .id("pnl")
                .updateTrigger(positionUpdateEob);

        netPnl = pnl.mapBiFunction(Mappers::subtractDoubles, feeStream)
                .updateTrigger(positionUpdateEob)
                .id("netPnl");
    }

    private void buildSinkOutputs() {

        //register position map sink endpoint
        positionMap
                .mapKeys(Instrument::instrumentName)
                .map(GroupBy::toMap)
                .id("positionMap")
                .sink("positionListener");

        //register fee sink endpoint
        feeStream
                .id("tradeFees")
                .sink("tradeFeesListener");

        //register rate map sink endpoint
        rateMap
                .mapKeys(Instrument::instrumentName)
                .map(GroupBy::toMap)
                .id("rates")
                .sink("rateListener");

        //register mtm position end point
        mtmPositionMap
                .mapKeys(Instrument::instrumentName)
                .map(GroupBy::toMap)
                .id("mtmPositionMap")
                .sink("mtmPositionListener");

        //register aggregate pnl sink endpoint
        pnl
                .filter(Double::isFinite)
                .sink("pnlListener");

        //register aggregate pnl sink endpoint
        netPnl
                .filter(Double::isFinite)
                .sink("netPnlListener");
    }
}