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
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@SepNode
public class PositionCache extends BaseNode {

    private Cache cache;
    private long sequenceNumber = 0;
    private PositionCacheSnapshot initialMap;

    @ServiceRegistered("positionCache")
    public void cacheRegistered(Cache cache) {
        this.cache = cache;
        sequenceNumber = cache.keys().stream().mapToLong(Long::parseLong).max().orElse(0);
        initialMap = cache.getOrDefault(sequenceNumber + "", new PositionCacheSnapshot());
        auditLog.info("cacheRegistered", cache)
                .info("keys", cache.keys().toString())
                .info("sequenceNumber", sequenceNumber)
                .info("initialMap", initialMap)
                .info();

        PositionSnapshot positionSnapshot = new PositionSnapshot();
        Collection<InstrumentPosition> positions = new ArrayList<>();

        initialMap.getInstrumentPosMtmMap().forEach((instrument, positionMtm) -> {
            double position = positionMtm.getPositionMap().get(instrument);
            InstrumentPosition instrumentPosition = new InstrumentPosition(instrument, position);
            positions.add(instrumentPosition);
        });
        positionSnapshot.setPositions(positions);

        getContext().getStaticEventProcessor().onEvent(positionSnapshot);
        getContext().getStaticEventProcessor().publishSignal(PnlCalculator.POSITION_UPDATE_EOB);
    }

    @OnEventHandler
    public boolean tradeIn(Trade trade) {
        sequenceNumber = Math.max(trade.getId(), sequenceNumber);
        auditLog.info("tradeId", trade.getId());
        return false;
    }

    public void mtmUpdated(Map<Instrument, InstrumentPosMtm> instrumentInstrumentPosMtmMap) {
        auditLog.info("mtmUpdated", instrumentInstrumentPosMtmMap);
        if (cache != null) {
            auditLog.info("cacheUpdateId", sequenceNumber);
            cache.put(sequenceNumber + "", new PositionCacheSnapshot(instrumentInstrumentPosMtmMap));
        }
    }

    public InstrumentPosMtm addInitialSnapshot(InstrumentPosMtm instrumentPosMtm) {
//        instrumentPosMtm.getPositionMap()
        auditLog.info("addInitialSnapshot", instrumentPosMtm);
        return instrumentPosMtm;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PositionCacheSnapshot {
        private Map<Instrument, InstrumentPosMtm> instrumentPosMtmMap = new HashMap<>();
    }
}
