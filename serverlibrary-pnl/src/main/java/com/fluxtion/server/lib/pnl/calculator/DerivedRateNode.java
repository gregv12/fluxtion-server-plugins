/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl.calculator;


import com.fluxtion.runtime.annotations.OnEventHandler;
import com.fluxtion.runtime.annotations.builder.FluxtionIgnore;
import com.fluxtion.runtime.dataflow.groupby.GroupBy;
import com.fluxtion.runtime.dataflow.groupby.GroupByHashMap;
import com.fluxtion.server.lib.pnl.MidPrice;
import com.fluxtion.server.lib.pnl.refdata.Instrument;
import com.fluxtion.server.lib.pnl.refdata.SymbolLookup;
import lombok.Data;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;

import java.util.Map;

@Data
public class DerivedRateNode {

    @FluxtionIgnore
    private final DefaultDirectedWeightedGraph<Instrument, DefaultWeightedEdge> graph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
    @FluxtionIgnore
    private final GroupByHashMap<Instrument, Double> derivedRates = new GroupByHashMap<>();
    @FluxtionIgnore
    private final DijkstraShortestPath<Instrument, DefaultWeightedEdge> shortestPath = new DijkstraShortestPath<>(graph);

    @OnEventHandler
    public boolean midRate(MidPrice midPrice) {
        Instrument dealtInstrument = midPrice.getSymbol().dealtInstrument();
        Instrument contraInstrument = midPrice.getSymbol().contraInstrument();
        if (dealtInstrument == contraInstrument) {
            return false;
        }
        double rate = midPrice.getRate();
        double logRate = Math.log(rate);
        double logInverseRate = Math.log(1 / rate);

        graph.addVertex(dealtInstrument);
        graph.addVertex(contraInstrument);
        if (graph.containsEdge(dealtInstrument, contraInstrument)) {
            graph.setEdgeWeight(dealtInstrument, contraInstrument, logRate);
            graph.setEdgeWeight(contraInstrument, dealtInstrument, logInverseRate);
        } else {
            graph.setEdgeWeight(graph.addEdge(dealtInstrument, contraInstrument), logRate);
            graph.setEdgeWeight(graph.addEdge(contraInstrument, dealtInstrument), logInverseRate);
        }
        return false;
    }

    public GroupBy<Instrument, Double> addDerived(
            GroupBy<Instrument, Double> rateMapGroupBy,
            GroupBy<Instrument, Double> positionMapGroupBy) {

        Map<Instrument, Double> rateMap = rateMapGroupBy.toMap();
        Map<Instrument, Double> positionMap = positionMapGroupBy.toMap();

        derivedRates.fromMap(rateMap);
        Map<Instrument, Double> derivedRateMap = derivedRates.toMap();

        positionMap.keySet().forEach(
                i -> {
                    derivedRateMap.computeIfAbsent(i, a -> {
                        double rate = shortestPath.getPathWeight(a, SymbolLookup.INSTRUMENT_USD);
                        return Double.isInfinite(rate) ? Double.NaN : Math.pow(10, rate);
                    });
                }
        );


//        rateMap.put(new Instrument("MOCA"), 0.5);
        return derivedRates;
    }

}
