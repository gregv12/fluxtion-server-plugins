/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl.talos;

import com.fluxtion.server.lib.pnl.*;
import com.fluxtion.server.lib.pnl.calculator.DerivedRateNode;
import com.fluxtion.server.lib.pnl.refdata.Instrument;
import com.fluxtion.server.lib.pnl.refdata.Symbol;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.fluxtion.server.lib.pnl.refdata.Instrument.INSTRUMENT_USD;
import static com.fluxtion.server.lib.pnl.refdata.Instrument.INSTRUMENT_USDT;

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
    public final static Symbol symbolEURGBP = new Symbol("EURGBP", EUR, GBP);
    private PnlCalculator pnlCalculator;
    private final boolean log = false;
    private final List<NetMarkToMarket> mtmUpdates = new ArrayList<>();
    private final List<Map<Instrument, NetMarkToMarket>> mtmInstUpdates = new ArrayList<>();

    @BeforeEach
    public void setUp() {
        pnlCalculator = new PnlCalculator();
        mtmUpdates.clear();
        mtmInstUpdates.clear();
        pnlCalculator.addAggregateMtMListener(m -> {
            mtmUpdates.add(m);
            if (log) {
                System.out.println("\n-------callback aggregate -------\n" + m);
            }
        });
        pnlCalculator.addInstrumentMtMListener(m -> {
            mtmInstUpdates.add(m);
            if (log) {
                System.out.println("\n-------callback instrument -------\n" + m);
            }
        });
    }

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
        setUp();
        pnlCalculator.addSymbol(symbolEURUSD);

        pnlCalculator.priceUpdate("EURCHF", 1.2);

        pnlCalculator.addSymbol(symbolEURCHF);
        Assertions.assertEquals(0, pnlCalculator.pnl());

        pnlCalculator.priceUpdate("EURCHF", 1.2);

        pnlCalculator.processTrade(new Trade(symbolEURCHF, 10, -12.5, 0));
        Assertions.assertTrue(Double.isNaN(pnlCalculator.pnl()));

        pnlCalculator.priceUpdate("EURUSD", 1.5);
        Assertions.assertEquals(-0.625, pnlCalculator.pnl(), 0.0000001);

        pnlCalculator.setMtmInstrument(CHF);
        Assertions.assertEquals(-0.5, pnlCalculator.pnl(), 0.0000001);

        pnlCalculator.setMtmInstrument(JPY);
        Assertions.assertTrue(Double.isNaN(pnlCalculator.pnl()));

        pnlCalculator.addSymbol(symbolEURJPY);
        pnlCalculator.priceUpdate("EURJPY", 200);
        Assertions.assertEquals(-83.333, pnlCalculator.pnl(), 0.001);
    }

    @Test
    public void testPositionSnapshot() {
        setUp();
        pnlCalculator.processTrade(new Trade(symbolUSDJPY, 100, -20000, 13));
        Assertions.assertEquals(1, mtmInstUpdates.size());
        Assertions.assertEquals(2, mtmInstUpdates.getFirst().size());
        Assertions.assertEquals(1, mtmUpdates.size());

        NetMarkToMarket mtm = mtmUpdates.getFirst();
        Map<Instrument, Double> positionMap = mtm.instrumentMtm().getPositionMap();
        Assertions.assertEquals(-20000, positionMap.get(JPY));
        Assertions.assertEquals(100, positionMap.get(USD));

        pnlCalculator.positionSnapshot(PositionSnapshot.of(
                new InstrumentPosition(EUR, 50),
                new InstrumentPosition(GBP, 12_000),
                new InstrumentPosition(INSTRUMENT_USD, 800),
                new InstrumentPosition(INSTRUMENT_USDT, 1500),
                new InstrumentPosition(INSTRUMENT_USD, 200)
        ));

        Assertions.assertEquals(2, mtmInstUpdates.size());
        Assertions.assertEquals(2, mtmInstUpdates.getFirst().size());
        Assertions.assertEquals(2, mtmUpdates.size());

        mtm = mtmUpdates.getLast();
        positionMap = mtm.instrumentMtm().getPositionMap();
        Assertions.assertEquals(50, positionMap.get(EUR));
        Assertions.assertEquals(12_000, positionMap.get(GBP));
        Assertions.assertEquals(-20000, positionMap.get(JPY));
        Assertions.assertEquals(1500, positionMap.get(INSTRUMENT_USDT));
        Assertions.assertEquals(200, positionMap.get(INSTRUMENT_USD));
    }


    @Test
    public void testTrade() {
        setUp();
        pnlCalculator.processTrade(new Trade(symbolEURJPY, -400, 80000, 13));
        pnlCalculator.processTrade(new Trade(symbolEURUSD, 500, -1100, 13));
        pnlCalculator.processTrade(new Trade(symbolUSDCHF, 500, -1100, 13));
        pnlCalculator.processTrade(new Trade(symbolEURGBP, 1200, -1000, 13));
        pnlCalculator.processTrade(new Trade(symbolGBPUSD, 1500, -700, 13));


        Assertions.assertEquals(5, mtmInstUpdates.size());
        Assertions.assertEquals(5, mtmInstUpdates.getFirst().size());
        Assertions.assertEquals(5, mtmUpdates.size());

        Map<Instrument, Double> positionMapFirst = mtmUpdates.getFirst().instrumentMtm().getPositionMap();
        Assertions.assertEquals(-400, positionMapFirst.get(EUR));
        Assertions.assertEquals(80000, positionMapFirst.get(JPY));

        Map<Instrument, Double> positionMap = mtmUpdates.getLast().instrumentMtm().getPositionMap();
        Assertions.assertEquals(1300, positionMap.get(EUR));
        Assertions.assertEquals(80000, positionMap.get(JPY));
        Assertions.assertEquals(-1300, positionMap.get(USD));
        Assertions.assertEquals(-1100, positionMap.get(CHF));
        Assertions.assertEquals(500, positionMap.get(GBP));
    }

    @Test
    public void testTradeBatch() {
        setUp();
        pnlCalculator.processTradeBatch(
                TradeBatch.of(200,
                        new Trade(symbolEURJPY, -400, 80000, 13),
                        new Trade(symbolEURUSD, 500, -1100, 13),
                        new Trade(symbolUSDCHF, 500, -1100, 13),
                        new Trade(symbolEURGBP, 1200, -1000, 13),
                        new Trade(symbolGBPUSD, 1500, -700, 13)
                )
        );

        Assertions.assertEquals(1, mtmInstUpdates.size());
        Assertions.assertEquals(5, mtmInstUpdates.getFirst().size());
        Assertions.assertEquals(1, mtmUpdates.size());

        Map<Instrument, Double> positionMap = mtmUpdates.getFirst().instrumentMtm().getPositionMap();
        Assertions.assertEquals(1300, positionMap.get(EUR));
        Assertions.assertEquals(80000, positionMap.get(JPY));
        Assertions.assertEquals(-1300, positionMap.get(USD));
        Assertions.assertEquals(-1100, positionMap.get(CHF));
        Assertions.assertEquals(500, positionMap.get(GBP));

        //fees
        Map<Instrument, Double> feePosMtm = mtmUpdates.getFirst().feesMtm().getFeesPositionMap();
        Assertions.assertEquals(1, feePosMtm.size());
        Assertions.assertEquals(65, feePosMtm.get(USD));
    }

    @Test
    public void testTradeBatchFeesDifferentInstrument() {
        setUp();
        pnlCalculator.processTradeBatch(
                TradeBatch.of(200,
                        new Trade(symbolEURJPY, -400, 80000, 10),
                        new Trade(symbolEURUSD, 500, -1100, 10),
                        new Trade(symbolUSDCHF, 500, -1100, 0.0005),
                        new Trade(symbolEURGBP, 1200, -1000, 10),
                        new Trade(symbolGBPUSD, 1500, -700, 50, GBP)
                )
        );

        Assertions.assertEquals(1, mtmInstUpdates.size());
        Assertions.assertEquals(5, mtmInstUpdates.getFirst().size());
        Assertions.assertEquals(1, mtmUpdates.size());

        Map<Instrument, Double> positionMap = mtmUpdates.getFirst().instrumentMtm().getPositionMap();
        Assertions.assertEquals(1300, positionMap.get(EUR));
        Assertions.assertEquals(80000, positionMap.get(JPY));
        Assertions.assertEquals(-1300, positionMap.get(USD));
        Assertions.assertEquals(-1100, positionMap.get(CHF));
        Assertions.assertEquals(500, positionMap.get(GBP));

        //fees
        Map<Instrument, Double> feePosMtm = mtmUpdates.getFirst().feesMtm().getFeesPositionMap();
        Assertions.assertEquals(2, feePosMtm.size());
        Assertions.assertEquals(50, feePosMtm.get(GBP));
        Assertions.assertEquals(30.0005, feePosMtm.get(USD));
    }

    @Test
    public void testMtm() {
        setUp();
        pnlCalculator.processTradeBatch(
                TradeBatch.of(200,
                        new Trade(symbolEURUSD, 500, -1000, 13),
                        new Trade(symbolGBPUSD, 1500, -2800, 13)
                )
        );


        Map<Instrument, Double> positionMap = mtmUpdates.getFirst().instrumentMtm().getPositionMap();
        Assertions.assertEquals(500, positionMap.get(EUR));
        Assertions.assertEquals(-3800, positionMap.get(USD));
        Assertions.assertEquals(1500, positionMap.get(GBP));

        Assertions.assertTrue(Double.isNaN(pnlCalculator.pnl()));

        pnlCalculator.priceUpdate(symbolGBPUSD, 2);
        Assertions.assertTrue(Double.isNaN(pnlCalculator.pnl()));

        pnlCalculator.priceUpdate(symbolEURUSD, 1.5);
        Assertions.assertEquals(-50, pnlCalculator.pnl(), 0.0000001);

        //no CHF rate force pnl to NaN
        pnlCalculator.processTrade(new Trade(symbolUSDCHF, 500, -1200, 13));
        Assertions.assertTrue(Double.isNaN(pnlCalculator.pnl()));

        //calc x-rate for usdchf : chf * eurchf -> eur,  eur * eurusd -> usd
        pnlCalculator.priceUpdate(symbolEURCHF, 3);
        Assertions.assertEquals(-150, pnlCalculator.pnl(), 0.0000001);
    }

    @Test
    public void testMtmMissingRateFOrZeroPosition() {
        setUp();
        pnlCalculator.processTradeBatch(
                TradeBatch.of(200,
                        new Trade(symbolEURUSD, 500, -1000, 13),
                        new Trade(symbolGBPUSD, 1500, -2800, 13)
                )
        );

        pnlCalculator.priceUpdate(symbolGBPUSD, 2);
        Assertions.assertTrue(Double.isNaN(pnlCalculator.pnl()));
        Map<Instrument, Double> positionMap = mtmUpdates.getFirst().instrumentMtm().getPositionMap();
        Assertions.assertEquals(500, positionMap.get(EUR));
        Assertions.assertEquals(-3800, positionMap.get(USD));
        Assertions.assertEquals(1500, positionMap.get(GBP));

        //clear EUR rate force pnl to NaN
        pnlCalculator.processTrade(new Trade(symbolEURUSD, -500, +1100, 13));

        positionMap = mtmUpdates.getLast().instrumentMtm().getPositionMap();
        Assertions.assertEquals(0, positionMap.get(EUR));
        Assertions.assertEquals(-2700, positionMap.get(USD));
        Assertions.assertEquals(1500, positionMap.get(GBP));

        Assertions.assertEquals(300, pnlCalculator.pnl(), 0.0000001);
    }

    @Test
    public void testFeesInDifferentInstrument() {
        setUp();
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
