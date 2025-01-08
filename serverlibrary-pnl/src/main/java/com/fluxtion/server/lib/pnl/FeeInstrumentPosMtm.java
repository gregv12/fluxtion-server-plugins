/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl;

import com.fluxtion.server.lib.pnl.refdata.Instrument;
import lombok.Data;
import lombok.extern.log4j.Log4j2;

import java.util.HashMap;
import java.util.Map;

@Data
@Log4j2
public class FeeInstrumentPosMtm {
    private String bookName;
    private Double fees = 0.0;
    private Map<Instrument, Double> feesPositionMap = new HashMap<>();
    private Map<Instrument, Double> feesMtmPositionMap = new HashMap<>();

    public static FeeInstrumentPosMtm addSnapshot(FeeInstrumentPosMtm instrumentPosMtm, InstrumentPosition instrumentPos) {
        FeeInstrumentPosMtm offSetPosMtm = new FeeInstrumentPosMtm().combine(instrumentPosMtm);
        if (instrumentPos != null) {
            offSetPosMtm.getFeesPositionMap().compute(
                    instrumentPos.instrument(),
                    (a, b) -> b == null ? instrumentPos.position() : b + instrumentPos.position());
        }
        return offSetPosMtm;
    }

    public static FeeInstrumentPosMtm merge(FeeInstrumentPosMtm leftPos, FeeInstrumentPosMtm rightPos) {
        return new FeeInstrumentPosMtm().combine(leftPos).combine(rightPos);
    }

    public FeeInstrumentPosMtm reset() {
        this.bookName = null;
        this.fees = 0.0;
        this.feesPositionMap.clear();
        this.feesMtmPositionMap.clear();
        return this;
    }

    public FeeInstrumentPosMtm resetMtm() {
        getFeesMtmPositionMap().clear();
        fees = 0.0;
        return this;
    }

    public FeeInstrumentPosMtm combine(FeeInstrumentPosMtm from) {
        if (from != null) {
            this.fees += from.fees;

            from.feesPositionMap.forEach((key, value) -> {
                feesPositionMap.merge(key, value, Double::sum);
            });

            from.feesMtmPositionMap.forEach((key, value) -> {
                feesMtmPositionMap.merge(key, value, Double::sum);
            });

            this.bookName = bookName == null ? from.bookName : bookName;
        }
        return this;
    }

    public double calcTradePnl() {
        return fees = feesMtmPositionMap.values().stream().mapToDouble(Double::doubleValue).sum();
    }
}
