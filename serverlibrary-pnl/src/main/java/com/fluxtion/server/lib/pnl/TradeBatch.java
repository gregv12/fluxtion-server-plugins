/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TradeBatch {
    private List<Trade> trades = new ArrayList<>();
    private double fee = 0;

    public static TradeBatch of(double fee, Trade... trades) {
        TradeBatch tradeBatch = new TradeBatch();
        for (Trade position : trades) {
            tradeBatch.getTrades().add(position);
        }
        tradeBatch.setFee(fee);
        return tradeBatch;
    }

    public static TradeBatch of(Trade... trades) {
        return TradeBatch.of(0.0, trades);
    }
}
