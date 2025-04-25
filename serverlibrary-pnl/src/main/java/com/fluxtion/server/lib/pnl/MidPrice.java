/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl;

import com.fluxtion.server.lib.pnl.refdata.Instrument;
import com.fluxtion.server.lib.pnl.refdata.Symbol;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MidPrice {
    private transient Symbol symbol;
    private String symbolName;
    private double rate;

    public MidPrice(Symbol symbol, double rate) {
        this.symbol = symbol;
        this.rate = rate;
        this.symbolName = symbol.symbolName();
    }

    public MidPrice(String symbolName, double rate) {
        this.symbolName = symbolName;
        this.rate = rate;
    }

    public Instrument dealtInstrument() {
        return getSymbol().dealtInstrument();
    }

    public Instrument contraInstrument() {
        return getSymbol().contraInstrument();
    }

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