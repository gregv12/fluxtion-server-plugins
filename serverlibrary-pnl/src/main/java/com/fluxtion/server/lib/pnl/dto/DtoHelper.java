/*
 *
 *  * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 *  * SPDX-License-Identifier: AGPL-3.0-only
 *
 */

package com.fluxtion.server.lib.pnl.dto;

import com.fluxtion.server.lib.pnl.NetMarkToMarket;

import java.util.HashMap;
import java.util.Map;

public interface DtoHelper {

    static Map<String, Double> formatPosition(NetMarkToMarket netMarkToMarket) {
        HashMap<String, Double> positionMap = new HashMap<>();
        netMarkToMarket.instrumentMtm().getPositionMap().forEach((k, v) -> {
            positionMap.put(k.instrumentName(), v);
        });
        return positionMap;
    }
}
