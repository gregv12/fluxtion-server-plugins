/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl.talos;

import com.fluxtion.runtime.dataflow.groupby.GroupBy;
import com.fluxtion.runtime.dataflow.groupby.GroupByHashMap;
import com.fluxtion.server.lib.pnl.MidPrice;
import com.fluxtion.server.lib.pnl.PnlCalculator;
import com.fluxtion.server.lib.pnl.Trade;
import com.fluxtion.server.lib.pnl.calculator.DerivedRateNode;
import com.fluxtion.server.lib.pnl.refdata.Instrument;
import com.fluxtion.server.lib.pnl.refdata.Symbol;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class DerivedTest {

    public static final Instrument EUR = new Instrument("EUR");
    public static final Instrument USD = new Instrument("USD");
    public static final Instrument CHF = new Instrument("CHF");
    public static final Instrument JPY = new Instrument("JPY");
    private final static Symbol symbolEURUSD = new Symbol("EURUSD", EUR, USD);
    private final static Symbol symbolEURCHF = new Symbol("EURCHF", EUR, CHF);
    private final static Symbol symbolUSDCHF = new Symbol("USDCHF", USD, CHF);
    private final static Symbol symbolEURJPY = new Symbol("EURJPY", EUR, JPY);
    private final static Symbol symbolUSDJPY = new Symbol("USDJPY", USD, JPY);

    @Test
    public void testCrossRate() {
        DerivedRateNode derivedRateNode = new DerivedRateNode();
        GroupByHashMap<Instrument, Double> rateMapGroupBy = new GroupByHashMap<>();
        GroupByHashMap<Instrument, Double> positionMapGroupBy = new GroupByHashMap<>();

        positionMapGroupBy.toMap().put(EUR, 500.0);

        derivedRateNode.midRate(new MidPrice(symbolEURUSD, 2));

        GroupBy<Instrument, Double> derivedRates = derivedRateNode.addDerived(rateMapGroupBy, positionMapGroupBy);
        Assertions.assertEquals(2, derivedRates.toMap().size());
        Assertions.assertEquals(2.0, derivedRates.toMap().get(EUR));
//        System.out.println("no directs only:" + derivedRates.toMap());

        derivedRateNode.midRate(new MidPrice(symbolEURCHF, 0.5));
        derivedRateNode.midRate(new MidPrice(symbolUSDCHF, 1.0));
        derivedRates = derivedRateNode.addDerived(rateMapGroupBy, positionMapGroupBy);
        Assertions.assertEquals(2, derivedRates.toMap().size());
        Assertions.assertEquals(0.5, derivedRates.toMap().get(EUR));
//        System.out.println("add a cross only:" + derivedRates.toMap());

        rateMapGroupBy.toMap().put(EUR, 1.6);
        derivedRates = derivedRateNode.addDerived(rateMapGroupBy, positionMapGroupBy);
        Assertions.assertEquals(2, derivedRates.toMap().size());
        Assertions.assertEquals(1.6, derivedRates.toMap().get(EUR));
//        System.out.println("directs only:" + derivedRates.toMap());
    }

    @Test
    public void testCalculator() {
        PnlCalculator pnlCalculator = new PnlCalculator();
//        pnlCalculator.addPositionListener(System.out::println);
//        pnlCalculator.addMtmPositionListener(System.out::println);
//        pnlCalculator.addRateListener(d -> System.out.println("rateMap:" + d));
//        pnlCalculator.addPnlListener(d -> System.out.println("pnl:" + d));
        pnlCalculator.addSymbol(symbolEURUSD);

        pnlCalculator.priceUpdate("EURCHF", 1.2);

        pnlCalculator.addSymbol(symbolEURCHF);
        Assertions.assertEquals(0, pnlCalculator.pnl());

        pnlCalculator.priceUpdate("EURCHF", 1.2);

//        System.out.println("\n send trade");
        pnlCalculator.processTrade(new Trade(symbolEURCHF, 10, -12.5, 0));
        Assertions.assertTrue(Double.isNaN(pnlCalculator.pnl()));

//        System.out.println("\nsend EURUSD rate");
        pnlCalculator.priceUpdate("EURUSD", 1.5);
        Assertions.assertEquals(-0.625, pnlCalculator.pnl(), 0.0000001);

//        System.out.println("\n mtm CHF");
        pnlCalculator.setMtmInstrument(CHF);
        Assertions.assertEquals(-0.5, pnlCalculator.pnl(), 0.0000001);

//        System.out.println("\n mtm JPY");
        pnlCalculator.setMtmInstrument(JPY);
        Assertions.assertTrue(Double.isNaN(pnlCalculator.pnl()));

        pnlCalculator.addSymbol(symbolEURJPY);
        pnlCalculator.priceUpdate("EURJPY", 200);
        Assertions.assertEquals(-83.333, pnlCalculator.pnl(), 0.001);
    }
}