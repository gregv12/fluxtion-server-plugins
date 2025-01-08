/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl.calculator;

import com.fluxtion.runtime.annotations.builder.SepNode;
import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.runtime.node.BaseNode;
import com.fluxtion.server.lib.pnl.Trade;
import com.fluxtion.server.plugin.cache.Cache;

@SepNode
public class TradeSequenceFilter extends BaseNode {

    private long sequenceNumber = 0;

    @ServiceRegistered("positionCache")
    public void cacheRegistered(Cache cache) {
        sequenceNumber = cache.keys().stream().mapToLong(Long::parseLong).max().orElse(0);
        auditLog.info("sequenceNumber", sequenceNumber);
    }

    public boolean checkTradeSequenceNumber(Trade trade) {
        boolean tradeIsNew = trade.getId() > sequenceNumber;
        auditLog.info("tradeIsNew", tradeIsNew)
                .info("sequenceNumber", sequenceNumber)
                .info("tradeId", trade.getId());
        if (tradeIsNew) {
            sequenceNumber = trade.getId();
        }
        return tradeIsNew;
    }
}
