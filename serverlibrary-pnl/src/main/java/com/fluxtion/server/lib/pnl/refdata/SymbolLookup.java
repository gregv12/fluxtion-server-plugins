/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl.refdata;

import java.util.Collection;

public interface SymbolLookup {

    Instrument INSTRUMENT_GBP = new Instrument("GBP", 1.0);
    Instrument INSTRUMENT_USD = new Instrument("USD", 1.0);
    Instrument INSTRUMENT_USDT = new Instrument("USDT", 1.0);

    Symbol SYMBOL_GBP = new Symbol(INSTRUMENT_GBP.instrumentName(), INSTRUMENT_GBP, INSTRUMENT_GBP);
    Symbol SYMBOL_USD = new Symbol(INSTRUMENT_USD.instrumentName(), INSTRUMENT_USD, INSTRUMENT_USD);
    Symbol SYMBOL_USDT = new Symbol(INSTRUMENT_USDT.instrumentName(), INSTRUMENT_USDT, INSTRUMENT_USD);

    Symbol getSymbolForName(String symbol);

    Collection<Symbol> symbols();
}
