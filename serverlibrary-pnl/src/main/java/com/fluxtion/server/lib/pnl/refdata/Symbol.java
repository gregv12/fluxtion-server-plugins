/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl.refdata;

import lombok.Data;

@Data
public final class Symbol {
    private String symbolName;
    private transient Instrument dealtInstrument;
    private transient Instrument contraInstrument;
    private String dealt;
    private String contra;

    public Symbol(String symbolName, Instrument dealtInstrument, Instrument contraInstrument) {
        this.symbolName = symbolName;
        this.dealtInstrument = dealtInstrument;
        this.contraInstrument = contraInstrument;
        this.dealt = dealtInstrument.instrumentName();
        this.contra = contraInstrument.instrumentName();
    }

    public Symbol(String symbolName, String dealtInstrument, String contraInstrument) {
        this(symbolName, new Instrument(dealtInstrument), new Instrument(contraInstrument));
    }

    public Symbol() {
    }

    public String symbolName() {
        return symbolName;
    }

    public Instrument dealtInstrument() {
        return dealtInstrument;
    }

    public Instrument contraInstrument() {
        return contraInstrument;
    }

    public void setDealt(String dealt) {
        this.dealt = dealt;
        this.dealtInstrument = new Instrument(dealt);
    }

    public void setContra(String contra) {
        this.contra = contra;
        this.contraInstrument = new Instrument(contra);
    }
}
