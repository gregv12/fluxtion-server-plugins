/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl.calculator;

import com.fluxtion.runtime.annotations.OnEventHandler;
import com.fluxtion.runtime.annotations.builder.SepNode;
import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.runtime.node.BaseNode;
import com.fluxtion.server.lib.pnl.InstrumentPosMtm;
import com.fluxtion.server.lib.pnl.Trade;
import com.fluxtion.server.lib.pnl.refdata.Instrument;
import com.fluxtion.server.plugin.cache.Cache;

import java.util.Collections;
import java.util.Map;

@SepNode
public class PositionCache extends BaseNode {

    private Cache cache;
    private long sequenceNumber = 0;
    private Map<Instrument, InstrumentPosMtm> initialMap;

    @ServiceRegistered("positionCache")
    public void cacheRegistered(Cache cache) {
        this.cache = cache;
        sequenceNumber = cache.keys().stream().mapToLong(Long::parseLong).max().orElse(0);
        initialMap = cache.getOrDefault(sequenceNumber + "", Collections.emptyMap());
        auditLog.info("cacheRegistered", cache)
                .info("keys", cache.keys().toString())
                .info("sequenceNumber", sequenceNumber)
                .info("initialMap", initialMap)
                .info();
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
            cache.put(sequenceNumber + "", instrumentInstrumentPosMtmMap);
        }
    }

}
