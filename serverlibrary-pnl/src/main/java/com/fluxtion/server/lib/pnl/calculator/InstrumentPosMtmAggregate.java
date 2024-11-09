/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl.calculator;

import com.fluxtion.runtime.dataflow.aggregate.AggregateFlowFunction;
import com.fluxtion.server.lib.pnl.InstrumentPosMtm;
import com.fluxtion.server.lib.pnl.Trade;

public class InstrumentPosMtmAggregate implements AggregateFlowFunction<Trade, InstrumentPosMtm, InstrumentPosMtmAggregate> {
    private InstrumentPosMtm instrumentPosMtm = new InstrumentPosMtm();
    private final boolean dealtSide;

    public InstrumentPosMtmAggregate(boolean dealtSide) {
        this.dealtSide = dealtSide;
    }

    public static InstrumentPosMtmAggregate dealt() {
        return new InstrumentPosMtmAggregate(true);
    }

    public static InstrumentPosMtmAggregate contra() {
        return new InstrumentPosMtmAggregate(false);
    }

    @Override
    public InstrumentPosMtm aggregate(Trade input) {
        final String bookName = dealtSide ? input.getDealtInstrument().instrumentName() : input.getContraInstrument().instrumentName();
        final double dealtPosition = input.getDealtVolume();
        final double contraPosition = input.getContraVolume();
        instrumentPosMtm.setBookName(bookName);
        //dealt and contra positions
        instrumentPosMtm.getPositionMap().compute(input.getDealtInstrument(), (s, d) -> d == null ? dealtPosition : d + dealtPosition);
        instrumentPosMtm.getPositionMap().compute(input.getContraInstrument(), (s, d) -> d == null ? contraPosition : d + contraPosition);
        return instrumentPosMtm;
    }

    @Override
    public InstrumentPosMtm get() {
        return instrumentPosMtm;
    }

    @Override
    public InstrumentPosMtm reset() {
        instrumentPosMtm = new InstrumentPosMtm();
        return instrumentPosMtm;
    }

}
