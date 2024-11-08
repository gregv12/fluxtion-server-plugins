/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl.talos;

import com.fluxtion.compiler.Fluxtion;
import com.fluxtion.compiler.builder.dataflow.DataFlow;
import com.fluxtion.compiler.builder.dataflow.FlowBuilder;
import com.fluxtion.compiler.builder.dataflow.GroupByFlowBuilder;
import com.fluxtion.compiler.builder.dataflow.JoinFlowBuilder;
import com.fluxtion.runtime.dataflow.groupby.GroupBy;
import com.fluxtion.server.lib.pnl.InstrumentPosMtm;
import com.fluxtion.server.lib.pnl.MidPrice;
import com.fluxtion.server.lib.pnl.NetMarkToMarket;
import com.fluxtion.server.lib.pnl.Trade;
import com.fluxtion.server.lib.pnl.calculator.FeeInstrumentPosMtmAggregate;
import com.fluxtion.server.lib.pnl.calculator.InstrumentPosMtmAggregate;
import com.fluxtion.server.lib.pnl.calculator.MtmCalc;
import com.fluxtion.server.lib.pnl.calculator.SingleInstrumentPosMtmAggregate;
import com.fluxtion.server.lib.pnl.refdata.Instrument;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.fluxtion.server.lib.pnl.talos.DerivedTest.*;

public class SampleCalc {


    private GroupByFlowBuilder<Instrument, Double> rateMap;

    @Test
    public void testCalc() {
        var calc = Fluxtion.compile(c -> {

            rateMap = DataFlow.subscribe(MidPrice.class)
                    .groupBy(MidPrice::dealtInstrument, MidPrice::getRate)
                    .defaultValue(GroupBy.emptyCollection());

            //per instrument
            GroupByFlowBuilder<Instrument, InstrumentPosMtm> dealtInstMtm = DataFlow.groupBy(Trade::getDealtInstrument, InstrumentPosMtmAggregate::dealt);
            GroupByFlowBuilder<Instrument, InstrumentPosMtm> contraInstMtm = DataFlow.groupBy(Trade::getContraInstrument, InstrumentPosMtmAggregate::contra);
            buildMtm(dealtInstMtm, contraInstMtm)
                    .sink("instrumentNetMtm")
            ;


            //mtm global
            GroupByFlowBuilder<Instrument, InstrumentPosMtm> globalDealtInstMtm = DataFlow.groupBy(Trade::getDealtInstrument, SingleInstrumentPosMtmAggregate::dealt);
            GroupByFlowBuilder<Instrument, InstrumentPosMtm> globalContraInstMtm = DataFlow.groupBy(Trade::getContraInstrument, SingleInstrumentPosMtmAggregate::contra);
            buildMtmGlobal(globalDealtInstMtm, globalContraInstMtm)
                    .map(GroupBy::toMap)
                    //reduce
                    .map(MtmCalc::markToMarketSum)
                    .sink("globalNetMtm")
            ;

        });

        calc.init();

        calc.addSink("instrumentNetMtm", m -> System.out.println("\n----mtm instrumentNetMtm-----\n" + m + "\n"));
        calc.addSink("globalNetMtm", m -> {
            System.out.println("\n----mtm globalNetMtm-----\n" + m + "\n");
        });

        calc.onEvent(new Trade(symbolEURCHF, 10, -12.5, 20, CHF));
        calc.onEvent(new Trade(symbolEURUSD, 200, -120.5, 50));
        calc.onEvent(new MidPrice(symbolEURUSD, 1.5));
        calc.onEvent(new MidPrice(symbolEURCHF, 2));
        calc.onEvent(new MidPrice(symbolCHFUSD, 3));
    }


    public FlowBuilder<Map<Instrument, NetMarkToMarket>> buildMtm(
            GroupByFlowBuilder<Instrument, InstrumentPosMtm> dealtInstMtm,
            GroupByFlowBuilder<Instrument, InstrumentPosMtm> contraInstMtm) {
        var instMtm = JoinFlowBuilder.outerJoin(dealtInstMtm, contraInstMtm, InstrumentPosMtm::merge)
                .mapBiFunction(MtmCalc::forTradeInstrumentPosMtm, rateMap);

        var instrumentFeeMap = DataFlow.groupBy(Trade::getDealtInstrument, FeeInstrumentPosMtmAggregate::new)
                .defaultValue(GroupBy.emptyCollection())
                .mapBiFunction(MtmCalc::forFeeInstrumentPosMtm, rateMap);

        return JoinFlowBuilder.leftJoin(instMtm, instrumentFeeMap, NetMarkToMarket::combine)
                .map(GroupBy::toMap);
    }

    public GroupByFlowBuilder<Instrument, NetMarkToMarket> buildMtmGlobal(
            GroupByFlowBuilder<Instrument, InstrumentPosMtm> dealtInstMtm,
            GroupByFlowBuilder<Instrument, InstrumentPosMtm> contraInstMtm) {

        var instMtm = JoinFlowBuilder.outerJoin(dealtInstMtm, contraInstMtm, InstrumentPosMtm::merge)
                .mapBiFunction(MtmCalc::forTradeInstrumentPosMtm, rateMap);

        var instrumentFeeMap = DataFlow.groupBy(Trade::getDealtInstrument, FeeInstrumentPosMtmAggregate::new)
                .defaultValue(GroupBy.emptyCollection())
                .mapBiFunction(MtmCalc::forFeeInstrumentPosMtm, rateMap);

        return JoinFlowBuilder.leftJoin(instMtm, instrumentFeeMap, NetMarkToMarket::combine);

//                .map(GroupBy::toMap);
    }
}
