/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl.talos;

import com.fluxtion.server.lib.pnl.Trade;
import com.fluxtion.server.lib.pnl.TradeBatch;
import com.fluxtion.server.lib.pnl.refdata.Symbol;
import com.fluxtion.server.lib.pnl.refdata.SymbolLookup;

public class TradeConverter {

    private final SymbolLookup symbolLookup;

    public TradeConverter(SymbolLookup symbolLookup) {
        this.symbolLookup = symbolLookup;
    }

    public Trade talosTradeToTrade(TradeDetails tradeDetails) {
        Symbol symbol = symbolLookup.getSymbolForName(tradeDetails.getSymbol());
        if (symbol == null) {
            return null;
        }
        int sign = tradeDetails.getSide().equalsIgnoreCase("buy") ? 1 : -1;
        double price = Double.parseDouble(tradeDetails.getPrice());
        double dealtQuantity = sign * Double.parseDouble(tradeDetails.getQuantity());
        double contraQuantity = -1 * sign * Double.parseDouble(tradeDetails.getAmount());
        double fee = Double.parseDouble(tradeDetails.getFee());

        return new Trade(symbol, dealtQuantity, contraQuantity, fee);
    }

    public TradeBatch talosTradeToTradeBatch(BookingRequest bookingRequest) {
        TradeBatch tb = new TradeBatch();
        for (TradeDetails tradeDetails : bookingRequest.getData()) {
            tb.getTrades().add(talosTradeToTrade(tradeDetails));
        }
        return tb;
    }
}
