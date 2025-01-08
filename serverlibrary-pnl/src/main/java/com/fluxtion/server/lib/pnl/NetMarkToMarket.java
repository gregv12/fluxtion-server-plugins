/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl;

import com.fluxtion.server.lib.pnl.refdata.Instrument;

import java.util.Map;

public record NetMarkToMarket(InstrumentPosMtm instrumentMtm, FeeInstrumentPosMtm feesMtm, double tradePnl, double fees,
                              double pnlNetFees) {

    public NetMarkToMarket(InstrumentPosMtm instrumentMtm, FeeInstrumentPosMtm feesMtm) {
        this(instrumentMtm, feesMtm, instrumentMtm.getTradePnl(), feesMtm.getFees(), (instrumentMtm.getTradePnl() - feesMtm.getFees()));
    }

    public static NetMarkToMarket combine(InstrumentPosMtm instrumentMtm, FeeInstrumentPosMtm feesMtm) {
        return new NetMarkToMarket(instrumentMtm, feesMtm == null ? new FeeInstrumentPosMtm() : feesMtm);
    }

    public static NetMarkToMarket markToMarketSum(Map<Instrument, NetMarkToMarket> instrumentNetMarkToMarketMap) {
        InstrumentPosMtm instrumentPosMtm = new InstrumentPosMtm();
        FeeInstrumentPosMtm feesMtm = new FeeInstrumentPosMtm();

        instrumentNetMarkToMarketMap.values().forEach(m -> {
            instrumentPosMtm.combine(m.instrumentMtm());
            feesMtm.combine(m.feesMtm());
        });
        instrumentPosMtm.setBookName("global");

        return new NetMarkToMarket(instrumentPosMtm, feesMtm);
    }

}
