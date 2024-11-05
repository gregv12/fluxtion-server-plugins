/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl.calculator;

import com.fluxtion.runtime.EventProcessorContext;
import com.fluxtion.runtime.EventProcessorContextListener;
import com.fluxtion.runtime.annotations.OnEventHandler;
import com.fluxtion.runtime.annotations.Start;
import com.fluxtion.server.lib.pnl.MidPrice;
import com.fluxtion.server.lib.pnl.refdata.Symbol;
import com.fluxtion.server.lib.pnl.refdata.SymbolLookup;

import java.util.Collection;

public class SymbolLookupNode
        implements
        com.fluxtion.server.lib.pnl.refdata.SymbolLookup,
        EventProcessorContextListener {

    private SymbolLookup symbolLookup;
    private EventProcessorContext currentContext;

    @Override
    public void currentContext(EventProcessorContext currentContext) {
        this.currentContext = currentContext;
    }

    @OnEventHandler
    public boolean setSymbolLookup(SymbolLookup symbolLookup) {
        this.symbolLookup = symbolLookup;
        return true;
    }

    @Start
    public void start() {
        symbolLookup.symbols().stream()
                .filter(s -> Double.isFinite(s.dealtInstrument().defaultUsdRate()))
                .map(s -> new MidPrice(s, INSTRUMENT_USD.defaultUsdRate()))
                .forEach(currentContext::processAsNewEventCycle);
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
