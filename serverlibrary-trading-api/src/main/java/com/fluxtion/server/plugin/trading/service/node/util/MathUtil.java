package com.fluxtion.server.plugin.trading.service.node.util;

/**
 * MathUtil provides simple rounding helpers used to conform prices and quantities
 * to venue precision requirements.
 */
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
}
