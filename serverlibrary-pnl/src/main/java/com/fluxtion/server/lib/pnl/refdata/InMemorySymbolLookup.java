/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl.refdata;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class InMemorySymbolLookup
        implements
        SymbolLookup {

    private final Map<String, Symbol> symbolMap = new HashMap<>();

    public InMemorySymbolLookup() {
    }

    @Override
    public Symbol getSymbolForName(String symbol) {
        return symbolMap.get(symbol);
    }

    @Override
    public Collection<Symbol> symbols() {
        return symbolMap.values();
    }

    public SymbolLookup addSymbol(Symbol symbol) {
        symbolMap.put(symbol.symbolName(), symbol);
        return this;
    }
}
