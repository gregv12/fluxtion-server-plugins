package com.fluxtion.server.lib.pnl.refdata;

import com.fluxtion.server.lib.pnl.MidPrice;

import java.util.List;

public interface DefaultRateMapSupplier {
    java.util.Map<String, Double> getSymbolRateMap();

    default List<MidPrice> getMidPrices(){
        return getSymbolRateMap().entrySet().stream()
                .filter(e -> {
                    String symbol = e.getKey();
                    return e.getKey().contains("-") && symbol.indexOf("-") < symbol.length() - 1;
                })
                .map(e -> {
                    String symbolName = e.getKey();
                    int index = symbolName.indexOf("-");
                    Symbol symbol = new Symbol(symbolName, symbolName.substring(0, index), symbolName.substring(index + 1));
                    return new MidPrice(symbol, e.getValue());
                })
                .toList();
    }
}
