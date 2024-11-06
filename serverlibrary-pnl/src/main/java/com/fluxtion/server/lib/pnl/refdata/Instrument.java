/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl.refdata;

public record Instrument(String instrumentName) {

    public static final Instrument INSTRUMENT_GBP = new Instrument("GBP");
    public static final Instrument INSTRUMENT_USD = new Instrument("USD");
    public static final Instrument INSTRUMENT_USDT = new Instrument("USDT");
}
