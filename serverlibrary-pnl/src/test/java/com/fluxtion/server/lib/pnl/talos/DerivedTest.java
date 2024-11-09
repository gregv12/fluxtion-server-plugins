/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl.talos;

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
    public static final Instrument GBP = new Instrument("GBP");
    public final static Symbol symbolEURUSD = new Symbol("EURUSD", EUR, USD);
    public final static Symbol symbolEURCHF = new Symbol("EURCHF", EUR, CHF);
    public final static Symbol symbolUSDCHF = new Symbol("USDCHF", USD, CHF);
    public final static Symbol symbolCHFUSD = new Symbol("CHFUSD", CHF, USD);
    public final static Symbol symbolEURJPY = new Symbol("EURJPY", EUR, JPY);
    public final static Symbol symbolUSDJPY = new Symbol("USDJPY", USD, JPY);
    public final static Symbol symbolGBPUSD = new Symbol("GBPUSD", GBP, USD);

    @Test
    public void testCrossRate() {
        DerivedRateNode derivedRateNode = new DerivedRateNode();
        derivedRateNode.midRate(new MidPrice(symbolEURCHF, 0.5));
        derivedRateNode.midRate(new MidPrice(symbolUSDCHF, 1.0));

        Assertions.assertEquals(0.5, derivedRateNode.getRateForInstrument(EUR));

        derivedRateNode.midRate(new MidPrice(symbolEURUSD, 1.6));
        Assertions.assertEquals(1.6, derivedRateNode.getRateForInstrument(EUR));
    }

    @Test
    public void testCalculator() {
        PnlCalculator pnlCalculator = new PnlCalculator();
//        pnlCalculator.addAggregateMtMListener(m -> System.out.println("\n-------callback aggregate -------\n" + m));
//        pnlCalculator.addInstrumentMtMListener(m -> System.out.println("\n-------callback instrument -------\n" + m));
        pnlCalculator.addSymbol(symbolEURUSD);

        pnlCalculator.priceUpdate("EURCHF", 1.2);

        pnlCalculator.addSymbol(symbolEURCHF);
        Assertions.assertEquals(0, pnlCalculator.pnl());

        pnlCalculator.priceUpdate("EURCHF", 1.2);

//        System.out.println("\n send trade");
        pnlCalculator.processTrade(new Trade(symbolEURCHF, 10, -12.5, 0));
        Assertions.assertTrue(Double.isNaN(pnlCalculator.pnl()));

        System.out.println("\nsend EURUSD rate");
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

    @Test
    public void testFeesInDifferentInstrument() {
        PnlCalculator pnlCalculator = new PnlCalculator();

        pnlCalculator.addSymbol(symbolEURUSD);
        pnlCalculator.addSymbol(symbolEURCHF);
        pnlCalculator.addSymbol(symbolGBPUSD);
        pnlCalculator.processTrade(new Trade(symbolEURCHF, 10, -12.5, 10, Instrument.INSTRUMENT_GBP));
        Assertions.assertTrue(Double.isNaN(pnlCalculator.pnl()));
        Assertions.assertTrue(Double.isNaN(pnlCalculator.tradeFees()));
        Assertions.assertTrue(Double.isNaN(pnlCalculator.netPnl()));
        System.out.println("----- trade complete -------");


        pnlCalculator.priceUpdate("EURCHF", 1.2);
        Assertions.assertTrue(Double.isNaN(pnlCalculator.pnl()));
        Assertions.assertTrue(Double.isNaN(pnlCalculator.tradeFees()));
        Assertions.assertTrue(Double.isNaN(pnlCalculator.netPnl()));
        System.out.println("----- EURCHF rate complete -------");


        pnlCalculator.priceUpdate("EURUSD", 1.5);
        //rates
        Assertions.assertEquals(1.5, pnlCalculator.getRateToMtmBase(EUR));
        Assertions.assertEquals(1.25, pnlCalculator.getRateToMtmBase(CHF));
        Assertions.assertTrue(Double.isNaN(pnlCalculator.getRateToMtmBase(GBP)));
        //mtm
        Assertions.assertEquals(-0.625, pnlCalculator.pnl(), 0.0000001);
        Assertions.assertTrue(Double.isNaN(pnlCalculator.tradeFees()));
        Assertions.assertTrue(Double.isNaN(pnlCalculator.netPnl()));
        System.out.println("----- EURUSD rate complete -------");


        pnlCalculator.priceUpdate("GBPUSD", 2);
        //rates
        Assertions.assertEquals(1.5, pnlCalculator.getRateToMtmBase(EUR));
        Assertions.assertEquals(1.25, pnlCalculator.getRateToMtmBase(CHF));
        Assertions.assertEquals(2.0, pnlCalculator.getRateToMtmBase(GBP));
        //mtm
        Assertions.assertEquals(-0.625, pnlCalculator.pnl(), 0.0000001);
        Assertions.assertEquals(20, pnlCalculator.tradeFees(), 0.0000001);
        Assertions.assertEquals(-20.625, pnlCalculator.netPnl(), 0.0000001);
        System.out.println("----- GBPUSD rate complete -------");
    }
}
