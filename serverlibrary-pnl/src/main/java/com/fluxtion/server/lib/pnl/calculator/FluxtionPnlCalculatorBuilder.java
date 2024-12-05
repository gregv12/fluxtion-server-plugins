/*
 *
 *  * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 *  * SPDX-License-Identifier: AGPL-3.0-only
 *
 */

package com.fluxtion.server.lib.pnl.calculator;

import com.fluxtion.compiler.EventProcessorConfig;
import com.fluxtion.compiler.FluxtionCompilerConfig;
import com.fluxtion.compiler.FluxtionGraphBuilder;
import com.fluxtion.compiler.builder.dataflow.DataFlow;
import com.fluxtion.compiler.builder.dataflow.FlowBuilder;
import com.fluxtion.compiler.builder.dataflow.GroupByFlowBuilder;
import com.fluxtion.compiler.builder.dataflow.JoinFlowBuilder;
import com.fluxtion.runtime.dataflow.groupby.GroupBy;
import com.fluxtion.runtime.event.Signal;
import com.fluxtion.server.lib.pnl.*;
import com.fluxtion.server.lib.pnl.dto.DtoHelper;
import com.fluxtion.server.lib.pnl.refdata.Instrument;
import lombok.Getter;

import static com.fluxtion.server.lib.pnl.PnlCalculator.GLOBAL_NET_MTM_SINK;
import static com.fluxtion.server.lib.pnl.PnlCalculator.INSTRUMENT_NET_MTM_SINK;

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
    private GroupByFlowBuilder<Instrument, FeeInstrumentPosMtm> instrumentFeeMap;
    private GroupByFlowBuilder<Instrument, InstrumentPosMtm> dealtAndContraInstPosition;
    private GroupByFlowBuilder<Instrument, InstrumentPosMtm> contraAndDealtInstPosition;
    private GroupByFlowBuilder<Instrument, InstrumentPosMtm> dealtOnlyInstPosition;
    private GroupByFlowBuilder<Instrument, InstrumentPosMtm> contraOnlyInstPosition;
    private DerivedRateNode derivedRateNode;
    private EventFeedConnector eventFeedConnector;

    public static FluxtionPnlCalculatorBuilder buildPnlCalculator(EventProcessorConfig config) {
        FluxtionPnlCalculatorBuilder calculatorBuilder = new FluxtionPnlCalculatorBuilder();
        calculatorBuilder.buildGraph(config);
        return calculatorBuilder;
    }

    @Override
    public void configureGeneration(FluxtionCompilerConfig compilerConfig) {
        compilerConfig.setClassName("FluxtionPnlCalculator");
        compilerConfig.setPackageName("com.fluxtion.server.lib.pnl.calculator");
    }

    @Override
    public void buildGraph(EventProcessorConfig eventProcessorConfig) {
        this.eventProcessorConfig = eventProcessorConfig;
        buildSharedNodes();
        buildTradeStream();
        buildPositionMap();
        buildInstrumentFeeMap();
        buildGlobalMtm();
        buildInstrumentMtm();

        //no buffer/trigger support required on this  processor
//        eventProcessorConfig.addEventAudit();
        eventProcessorConfig.setSupportBufferAndTrigger(false);
    }

    private void buildSharedNodes() {
        positionUpdateEob = DataFlow.subscribeToSignal("positionUpdate");
        positionSnapshotReset = DataFlow.subscribeToSignal("positionSnapshotReset");
        derivedRateNode = eventProcessorConfig.addNode(new DerivedRateNode(), "derivedRateNode");
        eventFeedConnector = eventProcessorConfig.addNode(new EventFeedConnector(), "eventFeedBatcher");
    }

    private void buildTradeStream() {
        tradeBatchStream = DataFlow.subscribe(TradeBatch.class).flatMap(TradeBatch::getTrades);
        tradeStream = DataFlow.subscribe(Trade.class).merge(tradeBatchStream);
    }

    private void buildPositionMap() {
        //position by instrument aggregates dealt and contra quantities
        dealtAndContraInstPosition = tradeStream
                .groupBy(Trade::getDealtInstrument, SingleInstrumentPosMtmAggregate::dealt)
                .resetTrigger(positionSnapshotReset);
        contraAndDealtInstPosition = tradeStream
                .groupBy(Trade::getContraInstrument, SingleInstrumentPosMtmAggregate::contra)
                .resetTrigger(positionSnapshotReset);

        //position by instrument aggregates single side, either dealt and contra quantity
        dealtOnlyInstPosition = tradeStream
                .groupBy(Trade::getDealtInstrument, InstrumentPosMtmAggregate::dealt)
                .resetTrigger(positionSnapshotReset)
                .publishTrigger(positionSnapshotReset);
        contraOnlyInstPosition = tradeStream
                .groupBy(Trade::getContraInstrument, InstrumentPosMtmAggregate::contra)
                .resetTrigger(positionSnapshotReset)
                .publishTrigger(positionSnapshotReset);
    }

    private void buildInstrumentFeeMap() {
        instrumentFeeMap = DataFlow.subscribe(Trade.class)
                .merge(tradeBatchStream)
                .groupBy(Trade::getDealtInstrument, FeeInstrumentPosMtmAggregate::new)
                .resetTrigger(positionSnapshotReset)
                .defaultValue(GroupBy.emptyCollection())
                .mapValues(derivedRateNode::calculateFeeMtm)
                .publishTriggerOverride(positionUpdateEob)
                .updateTrigger(positionUpdateEob);
    }

    private void buildGlobalMtm() {
        //Asset position map created by PositionSnapshot
        var snapshotPositionMap = DataFlow.subscribe(PositionSnapshot.class)
                .flatMap(PositionSnapshot::getPositions).id("flatMapSnapshotPositions")
                .groupBy(InstrumentPosition::instrument).id("groupBySnapshotPositions")
                .resetTrigger(positionSnapshotReset)
                .publishTriggerOverride(positionUpdateEob);

        //global mtm for trading + snapshot positions
        var globalNetMtm = JoinFlowBuilder.outerJoin(dealtAndContraInstPosition, contraAndDealtInstPosition, InstrumentPosMtm::merge)
                .outerJoin(snapshotPositionMap, InstrumentPosMtm::overwriteInstrumentPositionWithSnapshot)
                .mapValues(derivedRateNode::calculateInstrumentPosMtm)
                .updateTrigger(positionUpdateEob)
                .defaultValue(GroupBy.emptyCollection())
                .publishTriggerOverride(positionUpdateEob);

        //global mtm net of fees
        JoinFlowBuilder.leftJoin(globalNetMtm, instrumentFeeMap, NetMarkToMarket::combine)
                .updateTrigger(positionUpdateEob)
                .map(GroupBy::toMap)
                .map(NetMarkToMarket::markToMarketSum)
                .id("globalNetMtm")
                .sink(GLOBAL_NET_MTM_SINK)
                .map(DtoHelper::formatPosition)
                .sink(PnlCalculator.POSITION_SNAPSHOT_SINK);
    }

    private void buildInstrumentMtm() {
        //instrument mtm for trading
        var instNetMtm = JoinFlowBuilder.outerJoin(dealtOnlyInstPosition, contraOnlyInstPosition, InstrumentPosMtm::merge)
                .mapValues(derivedRateNode::calculateInstrumentPosMtm)
                .updateTrigger(positionUpdateEob)
                .defaultValue(GroupBy.emptyCollection())
                .publishTriggerOverride(positionUpdateEob);

        //instrument mtm net of fees
        JoinFlowBuilder.leftJoin(instNetMtm, instrumentFeeMap, NetMarkToMarket::combine)
                .updateTrigger(positionUpdateEob)
                .map(GroupBy::toMap)
                .id("instrumentNetMtm")
                .sink(INSTRUMENT_NET_MTM_SINK);
    }
}