/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl;

import com.fluxtion.server.lib.pnl.refdata.Instrument;
import com.fluxtion.server.lib.pnl.refdata.Symbol;
import lombok.Value;

@Value
public class Trade {
    Symbol symbol;
    double dealtVolume;
    double contraVolume;
    double fee;

    public Instrument getDealtInstrument() {
        return symbol.dealtInstrument();
    }

    public Instrument getContraInstrument() {
        return symbol.contraInstrument();
    }
}
