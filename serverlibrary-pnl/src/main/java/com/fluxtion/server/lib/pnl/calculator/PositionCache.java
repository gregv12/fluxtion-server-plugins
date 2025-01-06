/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl.calculator;

import com.fluxtion.runtime.annotations.OnEventHandler;
import com.fluxtion.runtime.annotations.builder.SepNode;
import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.runtime.node.BaseNode;
import com.fluxtion.server.lib.pnl.*;
import com.fluxtion.server.lib.pnl.refdata.Instrument;
import com.fluxtion.server.plugin.cache.Cache;
import lombok.Data;
import lombok.extern.log4j.Log4j2;

import java.util.HashMap;
import java.util.Map;

@SepNode
@Log4j2
public class PositionCache extends BaseNode {

    private Cache cache;
    private long sequenceNumber = 0;
    private ApplicationCheckpoint applicationCheckpoint = new ApplicationCheckpoint();

    @ServiceRegistered("positionCache")
    public void cacheRegistered(Cache cache) {
        this.cache = cache;
        sequenceNumber = cache.keys().stream().mapToLong(Long::parseLong).max().orElse(0);

        applicationCheckpoint = cache.getOrDefault(sequenceNumber + "", applicationCheckpoint);
        PositionSnapshot positionSnapshot = new PositionSnapshot();
        PositionCheckpoint positionCheckpoint = applicationCheckpoint.getGlobalPosition();
        Map<String, Double> positionMap = positionCheckpoint.getPositions();
        positionMap.forEach((inst, pos) -> positionSnapshot.getPositions().add(new InstrumentPosition(new Instrument(inst), pos)));

        Map<String, Double> feesMap = positionCheckpoint.getFees();
        feesMap.forEach((inst, pos) -> positionSnapshot.getFeePositions().add(new InstrumentPosition(new Instrument(inst), pos)));

        auditLog.info("cacheRegistered", cache)
                .info("keys", cache.keys().toString())
                .info("sequenceNumber", sequenceNumber)
                .info("positionMap", positionMap)
                .info("applicationCheckpoint", applicationCheckpoint)
                .info();

        getContext().getStaticEventProcessor().onEvent(positionSnapshot);
        getContext().getStaticEventProcessor().publishSignal(PnlCalculator.POSITION_UPDATE_EOB);
    }

    @OnEventHandler
    public boolean tradeIn(Trade trade) {
        sequenceNumber = Math.max(trade.getId(), sequenceNumber);
        auditLog.info("tradeId", trade.getId());
        return false;
    }

    public void mtmUpdated(NetMarkToMarket netMarkToMarket) {
        auditLog.info("netMarkToMarket", netMarkToMarket);
        PositionCheckpoint positionCheckpoint = applicationCheckpoint.getGlobalPosition();
        Map<String, Double> positionMap = positionCheckpoint.getPositions();
        Map<Instrument, Double> instrumentInstrumentPosMtmMap = netMarkToMarket.instrumentMtm().getPositionMap();
        instrumentInstrumentPosMtmMap.forEach((instrument, pos) -> {
            positionMap.put(instrument.getInstrumentName(), pos);
        });

        Map<String, Double> feesPositionMap = positionCheckpoint.getFees();
        Map<Instrument, Double> feesMap = netMarkToMarket.feesMtm().getFeesPositionMap();
        feesMap.forEach((instrument, pos) -> {
            feesPositionMap.put(instrument.getInstrumentName(), pos);
        });

        if (cache != null) {
            auditLog.info("cacheUpdateId", sequenceNumber);
            cache.put(sequenceNumber + "", applicationCheckpoint);
        }
    }

    public Object checkPoint(NetMarkToMarket netMarkToMarket, Map<Instrument, NetMarkToMarket> instrumentNetMarkToMarketMap) {
        log.info("checkPoint globalMtm:{}, instMtm:{}", netMarkToMarket, instrumentNetMarkToMarketMap);
        return null;
    }

    @Data
    public static class PositionCheckpoint {
        private Map<String, Double> fees = new HashMap<>();
        private Map<String, Double> positions = new HashMap<>();
    }

    @Data
    public static class ApplicationCheckpoint {
        private PositionCheckpoint globalPosition = new PositionCheckpoint();
        private Map<String, PositionCheckpoint> instrumentPositions = new HashMap<>();
    }
}
