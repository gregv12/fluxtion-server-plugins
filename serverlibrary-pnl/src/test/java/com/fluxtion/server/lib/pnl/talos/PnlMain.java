/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl.talos;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxtion.server.lib.pnl.*;
import com.fluxtion.server.lib.pnl.refdata.Instrument;
import com.fluxtion.server.lib.pnl.refdata.Symbol;

import java.util.Map;

import static com.fluxtion.server.lib.pnl.refdata.SymbolLookup.INSTRUMENT_USD;
import static com.fluxtion.server.lib.pnl.refdata.SymbolLookup.INSTRUMENT_USDT;


public class PnlMain {

    public static void main(String[] args) throws JsonProcessingException {
        //this encapsulates the fluxtion calculator for pnl
        PnlCalculator pnlCalculator = new PnlCalculator();

        Instrument moca = new Instrument("MOCA");
        Instrument ape = new Instrument("APE");
        Symbol mocaUsdtSymbol = new Symbol("MOCA-USDT", moca, INSTRUMENT_USDT);
        Symbol mocaUsdSymbol = new Symbol("MOCA-USD", moca, INSTRUMENT_USD);
        Symbol apeUsdtPerpSymbol = new Symbol("okx:APEUSDT-PERP", ape, INSTRUMENT_USDT);
        Symbol apeusdSymbol = new Symbol("APEUSD", ape, INSTRUMENT_USD);

        pnlCalculator
                //set symbols
                .addSymbol(mocaUsdtSymbol)
                .addSymbol(mocaUsdSymbol)
                .addSymbol(apeUsdtPerpSymbol)
                .addSymbol(apeusdSymbol)
                //publish some initial rates
                .priceUpdate("APEUSD", 2)
                //add listeners
                .addPnlListener(PnlMain::pnlUpdate)
                .addNetPnlListener(PnlMain::netPnlUpdate)
                .addTradeFeesListener(PnlMain::tradeFeesUpdate)
                .addPositionListener(PnlMain::positionUpdate)
                .addRateListener(PnlMain::rateUpdate)
                .addMtmPositionListener(PnlMain::mtmPositionUpdate);

        System.out.println("ratesMap - " + pnlCalculator.ratesMap());
        System.out.println("--- finish bootstrap rates ----\n");

        pnlCalculator.positionSnapshot(PositionSnapshot.of(
                new InstrumentPosition(ape, 50),
                new InstrumentPosition(moca, 12_000),
                new InstrumentPosition(INSTRUMENT_USDT, 1500),
                new InstrumentPosition(INSTRUMENT_USD, 200)
        ));
        System.out.println("--- finish snapshot positions ----\n");

        //send a block of trades in
        ObjectMapper objectMapper = new ObjectMapper();
        BookingRequest bookingRequest = objectMapper.readValue(input, BookingRequest.class);
        TradeConverter talosTradeConverter = new TradeConverter(pnlCalculator.getSymbolLookup());
        pnlCalculator.processCustomTrade(bookingRequest, talosTradeConverter::talosTradeToTradeBatch);
        System.out.println("--- finish process booking request ----\n");

        PositionSnapshot positionSnapshot = PositionSnapshot.of(
                new InstrumentPosition(ape, 500),
                new InstrumentPosition(INSTRUMENT_USDT, 10)
        );

        System.out.println();
        pnlCalculator.positionSnapshot(positionSnapshot);
        System.out.println("--- finish update snapshot positions ----\n");

        System.out.println();
        pnlCalculator.positionReset(positionSnapshot);
        System.out.println("--- finish reset positions with snapshot ----\n");

        System.out.println();
        pnlCalculator.positionReset();
        System.out.println("--- finish reset ----\n");

        pnlCalculator.processTrade(new Trade(apeusdSymbol, 500, -985, 13));
        System.out.println("--- finish trade add ----\n");

        pnlCalculator.priceUpdate("APEUSD", 3);
        System.out.println("--- finish price update ----\n");

        pnlCalculator.processTradeBatch(
                TradeBatch.of(200,
                        new Trade(apeusdSymbol, 500, -1100, 13),
                        new Trade(mocaUsdtSymbol, -2500, 1300, 13))
        );
        System.out.println("--- finish price trade batch ----\n");

        System.out.println("--- getters ---");
        System.out.println("get rates       - " + pnlCalculator.ratesMap());
        System.out.println("get position    - " + pnlCalculator.positionMap());
        System.out.println("get mtmPosition - " + pnlCalculator.mtmPositionMap() + " (USD)");
        System.out.println("get tradeFees   - " + pnlCalculator.tradeFees());
        System.out.println("get pnl         - " + pnlCalculator.pnl());
        System.out.println("get netPnl      - " + pnlCalculator.netPnl());
    }

    private static void mtmPositionUpdate(Map<String, Double> positionMap) {
        System.out.println("Callback:MtmPosition -> " + positionMap + " (USD)");
    }

    private static void positionUpdate(Map<String, Double> positionMap) {
        System.out.println("Callback:Position    -> " + positionMap);
    }

    private static void rateUpdate(Map<String, Double> positionMap) {
        System.out.println("Callback:Rates      -> " + positionMap);
    }

    public static void tradeFeesUpdate(double tradeFees) {
        System.out.printf("Callback:TradeFees   -> " + tradeFees + "\n");
    }

    public static void pnlUpdate(double pnl) {
        System.out.printf("Callback:Pnl         -> " + pnl + "\n");
    }

    public static void netPnlUpdate(double pnl) {
        System.out.printf("Callback:NetPnl      -> " + pnl + "\n");
    }

    public static String input = """
            {
              "reqid": 49,
              "type": "Trade",
              "seq": 148191,
              "ts": "2024-08-24T00:12:50.374191Z",
              "data": [
                {
                  "Timestamp": "2024-08-24T00:12:50.371040Z",
                  "Symbol": "okx:APEUSDT-PERP",
                  "TradeID": "7c443749-4abe-4b5c-8ae0-1729071b9c35",
                  "Side": "Buy",
                  "TransactTime": "2024-08-24T00:12:50.219000Z",
                  "Market": "okex",
                  "Price": "0.7150",
                  "Quantity": "1395",
                  "Amount": "99.742500",
                  "Fee": "0.024935625",
                  "FeeCurrency": "USDT",
                  "MarketTradeID": "164455914",
                  "TradeStatus": "Confirmed",
                  "AggressorSide": "Buy",
                  "OID": "1JPTBC1FKNG00",
                  "EID": "1JPTBC1GBNG00",
                  "AmountCurrency": "USDT",
                  "PriceAllIn": "0.71517875",
                  "TradeSource": "Market",
                  "Revision": 0,
                  "MarketAccount": "okex/okx-direct"
                },
                {
                  "Timestamp": "2024-08-24T00:12:50.371096Z",
                  "Symbol": "okx:APEUSDT-PERP",
                  "TradeID": "5a13b959-e04d-40c3-b3c3-aa5647e53997",
                  "Side": "Buy",
                  "TransactTime": "2024-08-24T00:12:50.220000Z",
                  "Market": "okex",
                  "Price": "0.7151",
                  "Quantity": "2",
                  "Amount": "0.143020",
                  "Fee": "0.000035755",
                  "FeeCurrency": "USDT",
                  "MarketTradeID": "164455915",
                  "TradeStatus": "Confirmed",
                  "AggressorSide": "Buy",
                  "OID": "1JPTBC1FKNG00",
                  "EID": "1JPTBC1GBNG01",
                  "AmountCurrency": "USDT",
                  "PriceAllIn": "0.71527878",
                  "TradeSource": "Market",
                  "Revision": 0,
                  "MarketAccount": "okex/okx-direct"
                }
              ]
            }
            """;

}
