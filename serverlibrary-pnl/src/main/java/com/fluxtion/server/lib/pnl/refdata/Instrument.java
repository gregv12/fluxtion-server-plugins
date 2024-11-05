/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl.refdata;

public record Instrument(String instrumentName, double defaultUsdRate) {
    public Instrument(String instrumentName) {
        this(instrumentName, Double.NaN);
    }
}
