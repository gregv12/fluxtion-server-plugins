/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl.calculator;

import com.fluxtion.runtime.annotations.OnEventHandler;
import com.fluxtion.server.lib.pnl.refdata.Symbol;
import com.fluxtion.server.lib.pnl.refdata.SymbolLookup;

import java.util.Collection;

public class SymbolLookupNode implements SymbolLookup {

    private SymbolLookup symbolLookup;

    @OnEventHandler
    public boolean setSymbolLookup(SymbolLookup symbolLookup) {
        this.symbolLookup = symbolLookup;
        return true;
    }

    @Override
    public Symbol getSymbolForName(String symbol) {
        return symbolLookup.getSymbolForName(symbol);
    }

    @Override
    public Collection<Symbol> symbols() {
        return symbolLookup.symbols();
    }
}
