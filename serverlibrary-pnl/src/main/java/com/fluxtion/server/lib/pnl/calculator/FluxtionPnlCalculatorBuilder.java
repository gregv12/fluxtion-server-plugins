/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
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
import com.fluxtion.runtime.audit.EventLogControlEvent;
import com.fluxtion.runtime.dataflow.groupby.GroupBy;
import com.fluxtion.runtime.event.Signal;
import com.fluxtion.runtime.node.NamedFeedTableNode;
import com.fluxtion.server.lib.pnl.*;
import com.fluxtion.server.lib.pnl.refdata.Instrument;
import com.fluxtion.server.lib.pnl.refdata.Symbol;
import lombok.Getter;

import java.util.Map;

import static com.fluxtion.server.lib.pnl.PnlCalculator.*;

/**
 * Builds the {@link FluxtionPnlCalculator} AOT using the Fluxtion maven plugin.
 * <p>
 * This build can be extended with a subclass or used as a utility to build a graph with the streaming nodes available
 * through accessors
 */

@Getter
public class FluxtionPnlCalculatorBuilder implements FluxtionGraphBuilder {

    private EventProcessorConfig eventProcessorConfig;
    private FlowBuilder<Signal> positionUpdateEob;
    private FlowBuilder<Signal> positionSnapshotReset;
    private FlowBuilder<Trade> tradeStream;
    private FlowBuilder<Trade> tradeBatchStream;
    private GroupByFlowBuilder<Instrument, InstrumentPosMtm> dealtInstPosition;
    private GroupByFlowBuilder<Instrument, InstrumentPosMtm> contraInstPosition;
    private GroupByFlowBuilder<Instrument, InstrumentPosMtm> dealtAndContraInstPosition;
    private GroupByFlowBuilder<Instrument, InstrumentPosMtm> contraAndDealtInstPosition;
    private DerivedRateNode derivedRateNode;
    private EventFeedConnector eventFeedConnector;
    private FlowBuilder<NetMarkToMarket> globalNetMtm;
    private FlowBuilder<Map<Instrument, NetMarkToMarket>> instrumentNetMtm;

    public static FluxtionPnlCalculatorBuilder buildPnlCalculator(EventProcessorConfig config) {
        FluxtionPnlCalculatorBuilder calculatorBuilder = new FluxtionPnlCalculatorBuilder();
        calculatorBuilder.buildGraph(config);
        return calculatorBuilder;
    }

    public static class DebugFluxtionPnlCalculatorBuilder extends FluxtionPnlCalculatorBuilder {

        @Override
        public void configureGeneration(FluxtionCompilerConfig compilerConfig) {
            compilerConfig.setClassName("DebugFluxtionPnlCalculator");
            compilerConfig.setPackageName("com.fluxtion.server.lib.pnl.calculator");
            compilerConfig.setGenerateDescription(false);
        }

        @Override
        public void buildGraph(EventProcessorConfig eventProcessorConfig) {
            super.buildGraph(eventProcessorConfig);
            eventProcessorConfig.addEventAudit(EventLogControlEvent.LogLevel.INFO);
        }
    }

    @Override
    public void configureGeneration(FluxtionCompilerConfig compilerConfig) {
        compilerConfig.setClassName("FluxtionPnlCalculator");
        compilerConfig.setPackageName("com.fluxtion.server.lib.pnl.calculator");
        compilerConfig.setGenerateDescription(false);
    }

    @Override
    public void buildGraph(EventProcessorConfig eventProcessorConfig) {
        this.eventProcessorConfig = eventProcessorConfig;
        buildSharedNodes();
        buildTradeStream();
        buildPositionMap();
        buildGlobalMtm();
        buildInstrumentMtm();
        buildCheckpoint();

        //no buffer/trigger support required on this  processor
        eventProcessorConfig.setSupportBufferAndTrigger(false);
    }

    private void buildSharedNodes() {
        positionUpdateEob = DataFlow.subscribeToSignal(PnlCalculator.POSITION_UPDATE_EOB);
        positionSnapshotReset = DataFlow.subscribeToSignal(PnlCalculator.POSITION_SNAPSHOT_RESET);
        var symbolTable = new NamedFeedTableNode<String, Symbol>("symbolFeed", Symbol::symbolName);
        derivedRateNode = eventProcessorConfig.addNode(new DerivedRateNode(symbolTable), "derivedRateNode");
        eventFeedConnector = eventProcessorConfig.addNode(new EventFeedConnector(symbolTable), "eventFeedBatcher");
    }

    private void buildTradeStream() {
        tradeBatchStream = DataFlow.subscribe(TradeBatch.class)
                .flatMap(TradeBatch::getTrades, POSITION_UPDATE_EOB)
                .filter(new TradeSequenceFilter()::checkTradeSequenceNumber)
                .map(eventFeedConnector::validateBatchTrade);

        tradeStream = DataFlow.subscribe(Trade.class)
                .map(eventFeedConnector::validateTrade)
                .filter(new TradeSequenceFilter(true)::checkTradeSequenceNumber)
                .merge(tradeBatchStream);
    }

    private void buildPositionMap() {
        //position by instrument aggregates single side, either dealt and contra quantity
        dealtInstPosition = tradeStream
                .groupBy(Trade::getDealtInstrument, SingleInstrumentPosMtmAggregate::dealt)
                .resetTrigger(positionSnapshotReset);
        contraInstPosition = tradeStream
                .groupBy(Trade::getContraInstrument, SingleInstrumentPosMtmAggregate::contra)
                .resetTrigger(positionSnapshotReset);

        //position by instrument aggregates dealt and contra quantities
        dealtAndContraInstPosition = tradeStream
                .groupBy(Trade::getDealtInstrument, InstrumentPosMtmAggregate::dealt)
                .resetTrigger(positionSnapshotReset)
                .publishTrigger(positionSnapshotReset);
        contraAndDealtInstPosition = tradeStream
                .groupBy(Trade::getContraInstrument, InstrumentPosMtmAggregate::contra)
                .resetTrigger(positionSnapshotReset)
                .publishTrigger(positionSnapshotReset);
    }

    private void buildGlobalMtm() {
        //Asset position map created by PositionSnapshot
        var snapshotPositionMap = DataFlow.subscribe(PositionSnapshot.class)
                .flatMap(PositionSnapshot::getPositions)
                .groupBy(InstrumentPosition::instrument)
                .resetTrigger(positionSnapshotReset)
                .publishTriggerOverride(positionUpdateEob);

        //global mtm for trading + snapshot positions
        var globalTradeMtm = JoinFlowBuilder.outerJoin(
                        dealtInstPosition,
                        contraInstPosition,
                        InstrumentPosMtm::merge)
                .publishTrigger(positionUpdateEob)
                .outerJoin(snapshotPositionMap, InstrumentPosMtm::addSnapshot)
                .mapValues(derivedRateNode::calculateInstrumentPosMtm)
                .updateTrigger(positionUpdateEob)
                .publishTriggerOverride(positionUpdateEob);

        //FeeInstrumentPosMtm
        var snapshotFeePositionMap = DataFlow.subscribe(PositionSnapshot.class)
                .flatMap(PositionSnapshot::getFeePositions)
                .groupBy(InstrumentPosition::instrument)
                .resetTrigger(positionSnapshotReset)
                .publishTriggerOverride(positionUpdateEob);

        var instrumentFeeMap = tradeStream
                .groupBy(Trade::getDealtInstrument, FeeInstrumentPosMtmAggregate::new)
                .publishTrigger(positionUpdateEob)
                .outerJoin(snapshotFeePositionMap, FeeInstrumentPosMtm::addSnapshot)
                .resetTrigger(positionSnapshotReset)
                .mapValues(derivedRateNode::calculateFeeMtm)
                .publishTriggerOverride(positionUpdateEob)
                .updateTrigger(positionUpdateEob);

        //global mtm net of fees
        globalNetMtm = JoinFlowBuilder.leftJoin(
                        globalTradeMtm,
                        instrumentFeeMap,
                        NetMarkToMarket::combine)
                .updateTrigger(positionUpdateEob)
                .map(GroupBy::toMap)
                .map(NetMarkToMarket::markToMarketSum)
                .id("globalNetMtm")
                .sink(GLOBAL_NET_MTM_SINK);
    }

    private void buildInstrumentMtm() {
        //instrument mtm fees mtm
        var instrumentFeeMap = JoinFlowBuilder.outerJoin(
                        tradeStream.groupBy(Trade::getDealtInstrument, FeeInstrumentPosMtmAggregate::new),
                        tradeStream.groupBy(Trade::getContraInstrument, FeeInstrumentPosMtmAggregate::new),
                        FeeInstrumentPosMtm::merge)
                .publishTrigger(positionUpdateEob)
                .outerJoin(
                        DataFlow.groupByFromMap(PositionSnapshot::getInstrumentFeePositionMap),
                        FeeInstrumentPosMtm::merge)
                .resetTrigger(positionSnapshotReset)
                .defaultValue(GroupBy.emptyCollection())//cant remove this
                .mapValues(derivedRateNode::calculateFeeMtm)
                .publishTriggerOverride(positionUpdateEob)
                .updateTrigger(positionUpdateEob);

        //instrument mtm for trading
        var instTradeMtm = JoinFlowBuilder.outerJoin(
                        dealtAndContraInstPosition,
                        contraAndDealtInstPosition,
                        InstrumentPosMtm::merge)
                .publishTrigger(positionUpdateEob)
                .outerJoin(
                        DataFlow.groupByFromMap(PositionSnapshot::getInstrumentPositionMap),
                        InstrumentPosMtm::mergeSnapshot)
                .mapValues(derivedRateNode::calculateInstrumentPosMtm)
                .updateTrigger(positionUpdateEob)
                .publishTriggerOverride(positionUpdateEob);

        //instrument mtm net of fees
        instrumentNetMtm = JoinFlowBuilder.leftJoin(
                        instTradeMtm,
                        instrumentFeeMap,
                        NetMarkToMarket::combine)
                .updateTrigger(positionUpdateEob)
                .map(GroupBy::toMap)
                .id("instrumentNetMtm")
                .sink(INSTRUMENT_NET_MTM_SINK);
    }

    private void buildCheckpoint() {
        PositionCache positionCache = eventProcessorConfig.addNode(new PositionCache(), "positionCache");
        DataFlow.mapBiFunction(positionCache::checkPoint, globalNetMtm, instrumentNetMtm);//this is persistent and aggregates trade instrument positions
    }
}