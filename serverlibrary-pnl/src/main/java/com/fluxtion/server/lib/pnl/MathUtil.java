/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl;

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
}
