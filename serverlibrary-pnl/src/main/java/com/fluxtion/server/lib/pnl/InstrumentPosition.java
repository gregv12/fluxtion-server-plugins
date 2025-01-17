/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl;


import com.fluxtion.server.lib.pnl.refdata.Instrument;

public record InstrumentPosition(Instrument instrument, double position) {
}
