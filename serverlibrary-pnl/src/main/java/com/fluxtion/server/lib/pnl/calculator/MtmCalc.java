/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl.calculator;

import com.fluxtion.runtime.dataflow.groupby.GroupBy;
import com.fluxtion.server.lib.pnl.FeeInstrumentPosMtm;
import com.fluxtion.server.lib.pnl.InstrumentPosMtm;
import com.fluxtion.server.lib.pnl.NetMarkToMarket;
import com.fluxtion.server.lib.pnl.refdata.Instrument;

import java.util.Map;

public class MtmCalc {

    public static GroupBy<Instrument, InstrumentPosMtm> forTradeInstrumentPosMtm(
            GroupBy<Instrument, InstrumentPosMtm> instrumentMtm,
            GroupBy<Instrument, Double> rates) {
        Map<Instrument, Double> ratesMap = rates.toMap();
        try {
            ratesMap.put(Instrument.INSTRUMENT_USD, 1.0);
            instrumentMtm.values().forEach(instrumentPosMtm -> {
                Map<Instrument, Double> mtmPositionsMap = instrumentPosMtm.resetMtm().getMtmPositionsMap();
                instrumentPosMtm.getPositionMap()
                        .forEach((key, value) -> {
                            mtmPositionsMap.put(key, value * ratesMap.getOrDefault(key, Double.NaN));
                        });
                instrumentPosMtm.calcTradePnl();
            });
        } catch (Exception e) {

        }
        return instrumentMtm;
    }

    public static GroupBy<Instrument, FeeInstrumentPosMtm> forFeeInstrumentPosMtm(
            GroupBy<Instrument, FeeInstrumentPosMtm> feeInstrumentMtm,
            GroupBy<Instrument, Double> rates) {
        Map<Instrument, Double> ratesMap = rates.toMap();
        try {
            ratesMap.put(Instrument.INSTRUMENT_USD, 1.0);
            feeInstrumentMtm.values().forEach(instrumentPosMtm -> {
                Map<Instrument, Double> mtmPositionsMap = instrumentPosMtm.resetMtm().getFeesMtmPositionMap();
                instrumentPosMtm.getFeesPositionMap()
                        .forEach((key, value) -> {
                            mtmPositionsMap.put(key, value * ratesMap.getOrDefault(key, Double.NaN));
                        });
                instrumentPosMtm.calcTradePnl();
            });
        } catch (Exception e) {

        }
        return feeInstrumentMtm;
    }

    public static NetMarkToMarket markToMarketSum(Map<Instrument, NetMarkToMarket> instrumentNetMarkToMarketMap) {

        InstrumentPosMtm instrumentPosMtm = new InstrumentPosMtm();
        FeeInstrumentPosMtm feesMtm = new FeeInstrumentPosMtm();


        instrumentNetMarkToMarketMap.values().forEach(m -> {
            instrumentPosMtm.combine(m.instrumentMtm());
            feesMtm.combine(m.feesMtm());
        });
        instrumentPosMtm.setBookName("global");

        NetMarkToMarket sumMtm = new NetMarkToMarket(instrumentPosMtm, feesMtm);
        return sumMtm;
    }
}
