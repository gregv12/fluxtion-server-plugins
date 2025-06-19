/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MidPriceBatch {
    private List<MidPrice> trades = new ArrayList<>();

    public static MidPriceBatch of(List<MidPrice> trades) {
        MidPriceBatch batch = new MidPriceBatch();
        batch.trades.addAll(trades);
        return batch;
    }

    @Override
    public String toString() {
        return "MidPriceBatch{" +
                "midPriceQuoteSize=" + trades.size() +
                '}';
    }
}
