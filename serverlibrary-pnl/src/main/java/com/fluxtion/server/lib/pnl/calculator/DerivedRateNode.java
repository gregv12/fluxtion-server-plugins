/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl.calculator;


import com.fluxtion.runtime.annotations.OnEventHandler;
import com.fluxtion.runtime.annotations.builder.FluxtionIgnore;
import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.runtime.event.NamedFeedEvent;
import com.fluxtion.runtime.input.NamedFeed;
import com.fluxtion.runtime.node.BaseNode;
import com.fluxtion.runtime.node.NamedFeedTableNode;
import com.fluxtion.server.lib.pnl.*;
import com.fluxtion.server.lib.pnl.refdata.Instrument;
import com.fluxtion.server.lib.pnl.refdata.Symbol;
import lombok.Data;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.BFSShortestPath;
import org.jgrapht.alg.shortestpath.BellmanFordShortestPath;
import org.jgrapht.alg.shortestpath.NegativeCycleDetectedException;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class DerivedRateNode extends BaseNode {

    private final NamedFeedTableNode<String, Symbol> symbolTable;
    @FluxtionIgnore
    private Instrument mtmInstrument = Instrument.INSTRUMENT_USD;
    @FluxtionIgnore
    private Map<String, Double> cachedRates = new HashMap<>();
    @FluxtionIgnore
    private Map<Instrument, Double> directMtmRatesByInstrument = new HashMap<>();
    @FluxtionIgnore
    private Map<Instrument, Double> derivedMtmRatesByInstrument = new HashMap<>();
    @FluxtionIgnore
    private final DefaultDirectedWeightedGraph<Instrument, DefaultWeightedEdge> graph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
    @FluxtionIgnore
    private final BellmanFordShortestPath<Instrument, DefaultWeightedEdge> shortestPath = new BellmanFordShortestPath<>(graph);
    @FluxtionIgnore
    private final BFSShortestPath<Instrument, DefaultWeightedEdge> simplePaths = new BFSShortestPath<>(graph);
    @FluxtionIgnore
    private boolean sendEob = true;

    public DerivedRateNode(NamedFeedTableNode<String, Symbol> symbolTable) {
        this.symbolTable = symbolTable;
    }

    @ServiceRegistered("rateFeed")
    public void serviceRegistered(NamedFeed feed) {
        auditLog.info("rateFeed", "registered");
        NamedFeedEvent<MidPrice>[] midPrices = feed.eventLog();
        sendEob = false;
        for (NamedFeedEvent<MidPrice> midPriceEvent : midPrices) {
            MidPrice midPrice = midPriceEvent.data();
            midRate(midPrice);
            auditLog.debug("midPrice", midPrice);
        }
        sendEob = true;
    }

    @OnEventHandler
    public boolean updateMtmInstrument(MtmInstrument mtmInstrumentUpdate) {
        boolean change = mtmInstrument != mtmInstrumentUpdate.instrument();
        if (change) {
            mtmInstrument = mtmInstrumentUpdate.instrument();
            directMtmRatesByInstrument.clear();
            derivedMtmRatesByInstrument.clear();
        }
        return change;
    }

    @OnEventHandler
    public boolean midRateBatch(MidPriceBatch midPriceBatch) {
        sendEob = false;
        List<MidPrice> trades = midPriceBatch.getTrades();
        for (int i = 0, tradesSize = trades.size(); i < tradesSize; i++) {
            MidPrice midPrice = trades.get(i);
            midRate(midPrice);
        }
        sendEob = true;
        if (context != null) {
            context.getStaticEventProcessor().publishSignal(PnlCalculator.POSITION_UPDATE_EOB);
        }
        return false;
    }

    @OnEventHandler
    public boolean midRate(MidPrice midPrice) {
        final double epsilon = 0.000000001d;
        double cachedRate = cachedRates.getOrDefault(midPrice.getSymbolName(), Double.NaN);
        double newRate = midPrice.getRate();

        if (Math.abs(cachedRate - newRate) < epsilon) {
            auditLog.debug("ignoreDuplicateRate", midPrice.getSymbolName())
                    .debug("cachedRate", cachedRate);
            return false;
        } else {
            auditLog.debug(midPrice.getSymbolName(), newRate);
        }
        cachedRates.put(midPrice.getSymbolName(), newRate);

        if (midPrice.getSymbol() == null) {
            midPrice.setSymbol(symbolTable.getTableMap().get(midPrice.getSymbolName()));
        }
        Instrument dealtInstrument = midPrice.dealtInstrument();
        Instrument contraInstrument = midPrice.contraInstrument();

        //no self cycles allowed
        if (dealtInstrument == contraInstrument | dealtInstrument == null | contraInstrument == null) {
            return false;
        }
        derivedMtmRatesByInstrument.clear();
        //add to directMtmRatesByInstrument
        if (midPrice.getOppositeInstrument(mtmInstrument) != null) {
            directMtmRatesByInstrument.put(midPrice.getOppositeInstrument(mtmInstrument), midPrice.getRateForInstrument(mtmInstrument));
        }

        double rate = midPrice.getRate();
        double logRate = Math.log10(rate);
        double logInverseRate = Math.log10(1 / rate);

        graph.addVertex(dealtInstrument);
        graph.addVertex(contraInstrument);
        if (graph.containsEdge(dealtInstrument, contraInstrument)) {
            graph.setEdgeWeight(dealtInstrument, contraInstrument, logRate);
            graph.setEdgeWeight(contraInstrument, dealtInstrument, logInverseRate);
        } else {
            graph.setEdgeWeight(graph.addEdge(dealtInstrument, contraInstrument), logRate);
            graph.setEdgeWeight(graph.addEdge(contraInstrument, dealtInstrument), logInverseRate);
        }

        if (context != null & sendEob) {
            context.getStaticEventProcessor().publishSignal(PnlCalculator.POSITION_UPDATE_EOB);
        }

        return false;
    }

    public FeeInstrumentPosMtm calculateFeeMtm(FeeInstrumentPosMtm feeInstrumentPosMtm) {
        Map<Instrument, Double> mtmPositionsMap = feeInstrumentPosMtm.resetMtm().getFeesMtmPositionMap();
        feeInstrumentPosMtm.getFeesPositionMap()
                .forEach((key, value) -> {
                    mtmPositionsMap.put(key, value == 0 ? 0 : value * getRateForInstrument(key));
                });
        feeInstrumentPosMtm.calcTradePnl();
        return feeInstrumentPosMtm;
    }

    public InstrumentPosMtm calculateInstrumentPosMtm(InstrumentPosMtm instrumentPosMtm) {
        Map<Instrument, Double> mtmPositionsMap = instrumentPosMtm.resetMtm().getMtmPositionsMap();
        instrumentPosMtm.getPositionMap()
                .forEach((key, value) -> {
                    mtmPositionsMap.put(key, value == 0 ? 0 : value * getRateForInstrument(key));
                });
        instrumentPosMtm.calcTradePnl();
        return instrumentPosMtm;
    }

    public Double getRateForInstrument(Instrument instrument) {
        if (instrument.equals(mtmInstrument)) {
            return 1.0;
        }
        Double rate = directMtmRatesByInstrument.get(instrument);
        if (rate == null) {
            rate = derivedMtmRatesByInstrument.computeIfAbsent(
                    instrument,
                    positionInstrument -> {
                        if (graph.containsVertex(positionInstrument) & graph.containsVertex(mtmInstrument)) {
                            double log10Rate;
                            try {
                                log10Rate = shortestPath.getPathWeight(positionInstrument, mtmInstrument);
                            } catch (NegativeCycleDetectedException e) {
                                GraphPath<Instrument, DefaultWeightedEdge> cycle = simplePaths.getPath(positionInstrument, mtmInstrument);
                                log10Rate = cycle.getWeight();
                            }
                            return Double.isInfinite(log10Rate) ? Double.NaN : Math.pow(10, log10Rate);
                        }
                        return Double.NaN;
                    });
        }
        return rate;
    }
}
