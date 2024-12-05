/*
 *
 *  * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 *  * SPDX-License-Identifier: AGPL-3.0-only
 *
 */

package com.fluxtion.server.lib.pnl.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TradeDto {
    private String symbol;
    private double dealtVolume;
    private double contraVolume;
    private double fee = 0.0;
    private String feeInstrument = "USD";

    public TradeDto(String symbol, double dealtVolume, double contraVolume) {
        this(symbol, dealtVolume, contraVolume, 0.0, "USD");
    }

    public TradeDto(String symbol, double dealtVolume, double contraVolume, double fee) {
        this(symbol, dealtVolume, contraVolume, fee, "USD");
    }
}
