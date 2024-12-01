/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl.talos;

import com.fluxtion.compiler.Fluxtion;
import com.fluxtion.compiler.builder.dataflow.DataFlow;
import com.fluxtion.compiler.builder.dataflow.JoinFlowBuilder;
import com.fluxtion.runtime.dataflow.groupby.GroupBy;
import com.fluxtion.server.lib.pnl.InstrumentPosMtm;
import com.fluxtion.server.lib.pnl.MidPrice;
import com.fluxtion.server.lib.pnl.Trade;
import com.fluxtion.server.lib.pnl.calculator.DerivedRateNode;
import com.fluxtion.server.lib.pnl.calculator.SingleInstrumentPosMtmAggregate;
import com.fluxtion.server.lib.pnl.refdata.Instrument;
import org.junit.jupiter.api.Test;

import static com.fluxtion.server.lib.pnl.talos.DerivedTest.*;

public class SimplePnl {

    @Test
    public void testPnl() {
        var pnlCalculator = Fluxtion.interpret(c -> {
                    var tradeStream = DataFlow.subscribe(Trade.class);
                    var dealtPosition = tradeStream.groupBy(Trade::getDealtInstrument, SingleInstrumentPosMtmAggregate::dealt);
                    var contraPosition = tradeStream.groupBy(Trade::getContraInstrument, SingleInstrumentPosMtmAggregate::contra);

                    var globalNetMtm = JoinFlowBuilder.outerJoin(dealtPosition, contraPosition, InstrumentPosMtm::merge)
                            .publishTrigger(DataFlow.subscribe(MidPrice.class))
                            .mapValues(new DerivedRateNode()::calculateInstrumentPosMtm)
                            .map(SimplePnl::calculateTotalPnl)
                            .sink("globalNetMtmListener");
                }
        );

        pnlCalculator.init();


        pnlCalculator.onEvent(new Trade(symbolEURJPY, -400, 80000, 13));
        pnlCalculator.onEvent(new Trade(symbolEURUSD, 500, -1100, 13));
        pnlCalculator.onEvent(new Trade(symbolUSDCHF, 500, -1100, 13));
        pnlCalculator.onEvent(new Trade(symbolEURGBP, 1200, -1000, 13));
        pnlCalculator.onEvent(new Trade(symbolGBPUSD, 1500, -700, 13));

        pnlCalculator.onEvent(new MidPrice(symbolEURGBP, 0.9));
        pnlCalculator.onEvent(new MidPrice(symbolEURUSD, 1.1));
        pnlCalculator.onEvent(new MidPrice(symbolEURCHF, 1.2));
        System.out.println("---------- final rate -----------");
        pnlCalculator.onEvent(new MidPrice(symbolEURJPY, 200));

        System.out.println("---------- final trade -----------");
        pnlCalculator.onEvent(new Trade(symbolGBPUSD, 20, -25, 13));
    }

    private static PnlSummary calculateTotalPnl(GroupBy<Instrument, InstrumentPosMtm> instrumentInstrumentPosMtmGroupBy) {
        System.out.println(instrumentInstrumentPosMtmGroupBy.toMap());
        return null;
    }

    private static class PnlSummary {

    }


}
