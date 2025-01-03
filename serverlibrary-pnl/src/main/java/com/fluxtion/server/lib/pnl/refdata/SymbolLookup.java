/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl.refdata;

import java.util.Collection;

public interface SymbolLookup {

    Symbol getSymbolForName(String symbol);

    Collection<Symbol> symbols();
}
