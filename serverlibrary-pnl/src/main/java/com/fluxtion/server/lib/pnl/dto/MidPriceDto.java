/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl.dto;

import lombok.Data;

@Data
public class MidPriceDto {
    private String symbol;
    private double price;
}
