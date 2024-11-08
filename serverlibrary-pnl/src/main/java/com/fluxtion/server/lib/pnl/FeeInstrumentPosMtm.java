/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl;

import com.fluxtion.server.lib.pnl.refdata.Instrument;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class FeeInstrumentPosMtm {
    private String bookName;
    private Double fees = 0.0;
    private Map<Instrument, Double> feesPositionMap = new HashMap<>();
    private Map<Instrument, Double> feesMtmPositionMap = new HashMap<>();

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
            this.feesPositionMap.putAll(from.feesPositionMap);
            this.feesMtmPositionMap.putAll(from.feesMtmPositionMap);
            this.bookName = bookName == null ? from.bookName : bookName;
        }
        return this;
    }

    public double calcTradePnl() {
        return fees = feesMtmPositionMap.values().stream().mapToDouble(Double::doubleValue).sum();
    }
}
