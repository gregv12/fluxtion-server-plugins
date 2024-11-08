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
import com.fluxtion.runtime.dataflow.helpers.Aggregates;
import com.fluxtion.runtime.event.Signal;
import com.fluxtion.server.lib.pnl.*;
import com.fluxtion.server.lib.pnl.refdata.Instrument;
import lombok.Getter;

import java.util.Map;

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
    private GroupByFlowBuilder<Instrument, Double> feePositionMp;
    protected GroupByFlowBuilder<Instrument, Double> rateMap;
    protected GroupByFlowBuilder<Instrument, Double> mtmPositionMap;
    protected GroupByFlowBuilder<Instrument, Double> mtmFeePositionMap;
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
        buildPositionMap();
        buildRateMap();
        buildInstrumentMtm();

        //no buffer/trigger support required on this  processor
        eventProcessorConfig.setSupportBufferAndTrigger(false);
    }

    private void buildInstrumentMtm() {
        //per instrument
        var dealtInstMtm = DataFlow.groupBy(Trade::getDealtInstrument, InstrumentPosMtmAggregate::dealt).resetTrigger(positionSnapshotReset);
        var contraInstMtm = DataFlow.groupBy(Trade::getContraInstrument, InstrumentPosMtmAggregate::contra).resetTrigger(positionSnapshotReset);
        buildMtm(dealtInstMtm, contraInstMtm)
                .id("instrumentNetMtm")
                .sink("instrumentNetMtmListener");


        //mtm global
        var globalDealtInstMtm = DataFlow.groupBy(Trade::getDealtInstrument, SingleInstrumentPosMtmAggregate::dealt);
        var globalContraInstMtm = DataFlow.groupBy(Trade::getContraInstrument, SingleInstrumentPosMtmAggregate::contra);
        buildMtmGlobal(globalDealtInstMtm, globalContraInstMtm)
                //reduce
                .map(MtmCalc::markToMarketSum)
                .id("globalNetMtm")
                .sink("globalNetMtmListener");
    }

    private FlowBuilder<Map<Instrument, NetMarkToMarket>> buildMtm(
            GroupByFlowBuilder<Instrument, InstrumentPosMtm> dealtInstMtm,
            GroupByFlowBuilder<Instrument, InstrumentPosMtm> contraInstMtm) {

        GroupByFlowBuilder<Instrument, InstrumentPosMtm> instMtm = JoinFlowBuilder.outerJoin(dealtInstMtm, contraInstMtm, InstrumentPosMtm::merge)
                .mapBiFunction(MtmCalc::forTradeInstrumentPosMtm, rateMap)
                .defaultValue(GroupBy.emptyCollection())
                .publishTriggerOverride(positionUpdateEob)
                .updateTrigger(positionUpdateEob);

        GroupByFlowBuilder<Instrument, FeeInstrumentPosMtm> instrumentFeeMap = DataFlow.groupBy(Trade::getDealtInstrument, FeeInstrumentPosMtmAggregate::new)
                .resetTrigger(positionSnapshotReset)
                .defaultValue(GroupBy.emptyCollection())
                .mapBiFunction(MtmCalc::forFeeInstrumentPosMtm, rateMap)
                .publishTriggerOverride(positionUpdateEob)
                .updateTrigger(positionUpdateEob);

        return JoinFlowBuilder.leftJoin(instMtm, instrumentFeeMap, NetMarkToMarket::combine)
                .updateTrigger(positionUpdateEob)
                .map(GroupBy::toMap);
    }

    private FlowBuilder<Map<Instrument, NetMarkToMarket>> buildMtmGlobal(
            GroupByFlowBuilder<Instrument, InstrumentPosMtm> dealtInstMtm,
            GroupByFlowBuilder<Instrument, InstrumentPosMtm> contraInstMtm) {
        var instMtm = JoinFlowBuilder.outerJoin(dealtInstMtm, contraInstMtm, InstrumentPosMtm::merge)
                .mapBiFunction(MtmCalc::forTradeInstrumentPosMtm, rateMap)
                .defaultValue(GroupBy.emptyCollection())
                .publishTriggerOverride(positionUpdateEob)
                .updateTrigger(positionUpdateEob);

        var instrumentFeeMap = DataFlow.groupBy(Trade::getDealtInstrument, FeeInstrumentPosMtmAggregate::new)
                .resetTrigger(positionSnapshotReset)
                .defaultValue(GroupBy.emptyCollection())
                .mapBiFunction(MtmCalc::forFeeInstrumentPosMtm, rateMap)
                .publishTriggerOverride(positionUpdateEob)
                .updateTrigger(positionUpdateEob);

        return JoinFlowBuilder.leftJoin(instMtm, instrumentFeeMap, NetMarkToMarket::combine)
                .updateTrigger(positionUpdateEob)
                .map(GroupBy::toMap);
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
        //
    }

    private void buildTradeStream() {
        var batchTradeStream = DataFlow.subscribe(TradeBatch.class)
                .flatMap(TradeBatch::getTrades);

        tradeStream = DataFlow.subscribe(Trade.class)
                .merge(batchTradeStream);
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
                                .resetTrigger(positionSnapshotReset),
                        MathUtil::addPositions)
                .publishTriggerOverride(positionUpdateEob)
                //join + add to snapshot map
                .outerJoin(snapshotPositionMap, MathUtil::addPositions)
                .defaultValue(GroupBy.emptyCollection())
                .updateTrigger(positionUpdateEob);

        feePositionMp = tradeStream.map(MathUtil::feePositionTrade)
                .merge(DataFlow.subscribe(TradeBatch.class).map(MathUtil::feePositionBatch))
                .groupBy(InstrumentPosition::instrument, InstrumentPosition::position, Aggregates.doubleSumFactory())
                .defaultValue(GroupBy.emptyCollection())
                .publishTriggerOverride(positionUpdateEob)
                .updateTrigger(positionUpdateEob);
    }

    private void buildRateMap() {
        //create a map of asset rates to USD, updates on any rate event
        DerivedRateNode derivedRateNode = new DerivedRateNode();
        rateMap = DataFlow.subscribe(MidPrice.class)
                .filter(derivedRateNode::isMtmSymbol)
                .groupBy(derivedRateNode::getMtmContraInstrument, derivedRateNode::getMtMRate)
                .resetTrigger(DataFlow.subscribe(MtmInstrument.class))
                .mapBiFunction(derivedRateNode::trimDerivedRates, positionMap)
                .mapBiFunction(derivedRateNode::addMissingDerivedRates, feePositionMp)
                .defaultValue(GroupBy.emptyCollection());
    }
}