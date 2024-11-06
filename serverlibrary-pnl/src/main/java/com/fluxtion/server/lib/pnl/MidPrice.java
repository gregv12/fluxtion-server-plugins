/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl;

import com.fluxtion.server.lib.pnl.refdata.Instrument;
import com.fluxtion.server.lib.pnl.refdata.Symbol;
import lombok.Value;

@Value
public class MidPrice {
    Symbol symbol;
    double rate;

    public double getRateForInstrument(Instrument ccy) {
        if (symbol.dealtInstrument().equals(ccy)) {
            return 1 / rate;
        } else if (symbol.contraInstrument().equals(ccy)) {
            return rate;
        }
        return Double.NaN;
    }

    public Instrument getOppositeInstrument(Instrument searchCcy) {
        if (symbol.dealtInstrument().equals(searchCcy)) {
            return symbol.contraInstrument();
        } else if (symbol.contraInstrument().equals(searchCcy)) {
            return symbol.dealtInstrument();
        }
        return null;
    }
}