/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl;

public record NetMarkToMarket(InstrumentPosMtm instrumentMtm, FeeInstrumentPosMtm feesMtm, double tradePnl, double fees,
                              double pnlNetFees) {

    public NetMarkToMarket(InstrumentPosMtm instrumentMtm, FeeInstrumentPosMtm feesMtm) {
        this(instrumentMtm, feesMtm, instrumentMtm.getTradePnl(), feesMtm.getFees(), (instrumentMtm.getTradePnl() - feesMtm.getFees()));
    }

    public static NetMarkToMarket combine(InstrumentPosMtm instrumentMtm, FeeInstrumentPosMtm feesMtm) {
        return new NetMarkToMarket(instrumentMtm, feesMtm == null ? new FeeInstrumentPosMtm() : feesMtm);
    }

}
