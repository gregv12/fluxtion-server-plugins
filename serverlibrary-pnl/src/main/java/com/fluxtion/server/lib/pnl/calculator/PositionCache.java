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

import java.util.HashMap;
import java.util.Map;

@SepNode
public class PositionCache extends BaseNode {

    private Cache cache;
    private long sequenceNumber = 0;

    @ServiceRegistered("positionCache")
    public void cacheRegistered(Cache cache) {
        this.cache = cache;
        sequenceNumber = cache.keys().stream().mapToLong(Long::parseLong).max().orElse(0);
        Map<String, Double> positionMap = cache.getOrDefault(sequenceNumber + "", new HashMap<>());
        PositionSnapshot positionSnapshot = new PositionSnapshot();
        positionMap.forEach((inst, pos) -> positionSnapshot.getPositions().add(new InstrumentPosition(new Instrument(inst), pos)));

        auditLog.info("cacheRegistered", cache)
                .info("keys", cache.keys().toString())
                .info("sequenceNumber", sequenceNumber)
                .info("positionMap", positionMap)
                .info("positionSnapshot", positionSnapshot)
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

    public void mtmUpdated(Map<Instrument, InstrumentPosMtm> instrumentInstrumentPosMtmMap) {
        auditLog.info("mtmUpdated", instrumentInstrumentPosMtmMap);
        HashMap<String, Double> positionMap = new HashMap<>();
        instrumentInstrumentPosMtmMap.forEach((instrument, instrumentPosMtm) -> {
            positionMap.put(
                    instrument.getInstrumentName(),
                    instrumentPosMtm.getPositionMap().get(instrument));
        });
        if (cache != null) {
            auditLog.info("cacheUpdateId", sequenceNumber);
            cache.put(sequenceNumber + "", positionMap);
        }
    }

    @Data
    public static class MtmCheckpoint {
        private Map<String, Double> fees = new HashMap<>();
        private Map<String, Double> positions = new HashMap<>();
    }
}
