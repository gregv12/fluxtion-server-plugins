/*
 *
 *  * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 *  * SPDX-License-Identifier: AGPL-3.0-only
 *
 */

package com.fluxtion.server.lib.pnl.refdata;

public record Symbol(String symbolName, Instrument dealtInstrument, Instrument contraInstrument) {

    public Symbol(String symbolName, String dealtInstrument, String contraInstrument) {
        this(symbolName, new Instrument(dealtInstrument), new Instrument(contraInstrument));
    }
}
