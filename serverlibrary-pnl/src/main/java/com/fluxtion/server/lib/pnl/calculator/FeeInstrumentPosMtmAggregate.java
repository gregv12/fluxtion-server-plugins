/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl.calculator;

import com.fluxtion.runtime.dataflow.aggregate.AggregateFlowFunction;
import com.fluxtion.server.lib.pnl.FeeInstrumentPosMtm;
import com.fluxtion.server.lib.pnl.Trade;

public class FeeInstrumentPosMtmAggregate implements AggregateFlowFunction<Trade, FeeInstrumentPosMtm, FeeInstrumentPosMtmAggregate> {
    private FeeInstrumentPosMtm feeInstrumentPosMtm = new FeeInstrumentPosMtm();

    @Override
    public FeeInstrumentPosMtm aggregate(Trade input) {
        final String bookName = input.getFeeInstrument().instrumentName();
        final double feePosition = input.getFee();
        feeInstrumentPosMtm.setBookName(bookName);
        feeInstrumentPosMtm.getFeesPositionMap().compute(input.getFeeInstrument(), (s, d) -> d == null ? feePosition : d + feePosition);
        return feeInstrumentPosMtm;
    }

    @Override
    public FeeInstrumentPosMtm get() {
        return feeInstrumentPosMtm;
    }

    @Override
    public FeeInstrumentPosMtm reset() {
        feeInstrumentPosMtm = new FeeInstrumentPosMtm();
        return feeInstrumentPosMtm;
    }

    public static FeeInstrumentPosMtm merge(FeeInstrumentPosMtm leftPos, FeeInstrumentPosMtm rightPos) {
        return new FeeInstrumentPosMtm().combine(leftPos).combine(rightPos);
    }
}