/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl;

import com.fluxtion.server.lib.pnl.refdata.Instrument;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TradeBatch {
    private List<Trade> trades = new ArrayList<>();
    private Instrument feeInstrument = Instrument.INSTRUMENT_USD;
    private double fee = 0;

    public static TradeBatch of(Trade... trades) {
        return TradeBatch.of(0.0, trades);
    }

    public static TradeBatch of(double fee, Trade... trades) {
        return TradeBatch.of(fee, Instrument.INSTRUMENT_USD, trades);
    }

    public static TradeBatch of(double fee, Instrument feeInstrument, Trade... trades) {
        TradeBatch tradeBatch = new TradeBatch();
        tradeBatch.feeInstrument = feeInstrument;
        for (Trade position : trades) {
            tradeBatch.getTrades().add(position);
        }
        tradeBatch.setFee(fee);
        return tradeBatch;
    }

    @Override
    public String toString() {
        return "TradeBatch{" +
                "tradesSize=" + trades.size() +
                ", feeInstrument=" + feeInstrument +
                ", fee=" + fee +
                '}';
    }
}
