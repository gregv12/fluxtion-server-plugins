/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl.calculator;

import com.fluxtion.runtime.dataflow.aggregate.AggregateFlowFunction;
import com.fluxtion.server.lib.pnl.InstrumentPosMtm;
import com.fluxtion.server.lib.pnl.Trade;

public class SingleInstrumentPosMtmAggregate implements AggregateFlowFunction<Trade, InstrumentPosMtm, SingleInstrumentPosMtmAggregate> {
    private InstrumentPosMtm instrumentPosMtm = new InstrumentPosMtm();

    public static SingleInstrumentPosMtmAggregate dealt() {
        return new SingleInstrumentPosMtmAggregate(true);
    }

    public static SingleInstrumentPosMtmAggregate contra() {
        return new SingleInstrumentPosMtmAggregate(false);
    }

    public SingleInstrumentPosMtmAggregate(boolean dealtSide) {
        this.dealtSide = dealtSide;
    }

    private final boolean dealtSide;

    @Override
    public InstrumentPosMtm get() {
        return instrumentPosMtm;
    }

    @Override
    public InstrumentPosMtm aggregate(Trade input) {
        if (dealtSide) {
            instrumentPosMtm.setBookName(input.getDealtInstrument().instrumentName());
            final double dealtPosition = input.getDealtVolume();
            instrumentPosMtm.getPositionMap().compute(input.getDealtInstrument(), (s, d) -> d == null ? dealtPosition : d + dealtPosition);
        } else {
            instrumentPosMtm.setBookName(input.getContraInstrument().instrumentName());
            final double contraPosition = input.getContraVolume();
            instrumentPosMtm.getPositionMap().compute(input.getContraInstrument(), (s, d) -> d == null ? contraPosition : d + contraPosition);
        }
        return instrumentPosMtm;
    }

    @Override
    public InstrumentPosMtm reset() {
        instrumentPosMtm = new InstrumentPosMtm();
        return instrumentPosMtm;
    }

}
