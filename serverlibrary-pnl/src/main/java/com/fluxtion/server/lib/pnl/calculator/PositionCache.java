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

    public static final String ID = "positionCache";
    private Cache cache;
    private long sequenceNumber = 0;
    private ApplicationCheckpoint applicationCheckpoint = new ApplicationCheckpoint();

    @ServiceRegistered(ID)
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

        //
        applicationCheckpoint.instrumentPositions.forEach((instString, posCheckpoint) -> {
            Instrument instrument = new Instrument(instString);
            var instPosSnapshot = positionSnapshot.getInstrumentPositionSnopshotMap()
                    .computeIfAbsent(instrument, instKey -> new PositionSnapshot.InstrumentPositionSnapshot());
            //trade positions for an instrument
            posCheckpoint.getPositions()
                    .forEach((inst, pos) -> {
                        instPosSnapshot.getPositions().add(new InstrumentPosition(new Instrument(inst), pos));
                    });
            //fee positions for an instrument
            posCheckpoint.getFees()
                    .forEach((inst, posFee) -> {
                        instPosSnapshot.getFeePositions().add(new InstrumentPosition(new Instrument(inst), posFee));
                    });
        });

        auditLog.info("cacheRegistered", cache)
                .info("keys", cache.keys().toString())
                .info("sequenceNumber", sequenceNumber)
                .info("positionMap", positionMap)
                .info("applicationCheckpoint", applicationCheckpoint)
                .info();

        log.debug("loaded checkpoint:{}\nsnapshot:{}", applicationCheckpoint, positionSnapshot);
        getContext().getStaticEventProcessor().onEvent(positionSnapshot);
    }

    @OnEventHandler
    public boolean tradeIn(Trade trade) {
        sequenceNumber = Math.max(trade.getId(), sequenceNumber);
        auditLog.info("tradeId", trade.getId());
        return false;
    }

    @OnEventHandler
    public boolean tradeIn(TradeBatch tradeBatch) {
        tradeBatch.getTrades().forEach(this::tradeIn);
        return false;
    }

    public Object checkPoint(NetMarkToMarket netMarkToMarket, Map<Instrument, NetMarkToMarket> instrumentNetMarkToMarketMap) {
        mtmUpdated(netMarkToMarket, applicationCheckpoint.getGlobalPosition());

        instrumentNetMarkToMarketMap.forEach((instrument, netMtm) -> {
            var checkpoint = applicationCheckpoint.instrumentPositions.computeIfAbsent(
                    instrument.getInstrumentName(), inst -> new PositionCheckpoint());
            mtmUpdated(netMtm, checkpoint);
        });

        if (cache != null) {
            auditLog.info("cacheUpdateId", sequenceNumber);
            cache.put(sequenceNumber + "", applicationCheckpoint);
        }
        log.debug("checkPoint:{} \nglobalMtm:{} \ninstMtm:{} \ncheckPoint{}", sequenceNumber, netMarkToMarket, instrumentNetMarkToMarketMap, applicationCheckpoint);
        return null;
    }

    private void mtmUpdated(NetMarkToMarket netMarkToMarket, PositionCheckpoint positionCheckpoint) {
        auditLog.info("netMarkToMarket", netMarkToMarket);
        positionCheckpoint.setNetMarkToMarket(netMarkToMarket);

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
    }

    @Data
    public static class PositionCheckpoint {
        private Map<String, Double> fees = new HashMap<>();
        private Map<String, Double> positions = new HashMap<>();
        private NetMarkToMarket netMarkToMarket;

    }
    @Data
    public static class ApplicationCheckpoint {
        private PositionCheckpoint globalPosition = new PositionCheckpoint();
        private Map<String, PositionCheckpoint> instrumentPositions = new HashMap<>();
    }
}
