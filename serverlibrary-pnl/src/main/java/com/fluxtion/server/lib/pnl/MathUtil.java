/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl;

import com.fluxtion.runtime.dataflow.Tuple;

public interface MathUtil {

    static double truncate(double amount, int decimalPlaces) {
        int rounding = (int) Math.pow(10, decimalPlaces);
        if (amount < 0) {
            return Math.ceil(amount * rounding) / rounding;
        }
        return Math.floor(amount * rounding) / rounding;
    }

    static double round(double amount, int decimalPlaces) {
        double rounding = (int) Math.pow(10, decimalPlaces);
        return Math.round(amount * rounding) / rounding;
    }

    static double round8dp(double amount) {
        return round(amount, 8);
    }

    static double addPositions(Double pos1, Double pos2) {
        double position1 = pos1 == null ? 0 : pos1;
        double position2 = pos2 == null ? 0 : pos2;
        return MathUtil.round(position1 + position2, 8);
    }

    static double mergeRates(Tuple<Double, Double> tuple) {
        return tuple.getFirst() == null ? tuple.getSecond() : tuple.getFirst();
    }

    static InstrumentPosition feePositionTrade(Trade trade) {
        return new InstrumentPosition(trade.getFeeInstrument(), trade.getFee());
    }

    static InstrumentPosition feePositionBatch(TradeBatch trade) {
        return new InstrumentPosition(trade.getFeeInstrument(), trade.getFee());
    }
}
