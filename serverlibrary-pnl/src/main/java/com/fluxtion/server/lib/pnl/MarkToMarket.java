/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl;

import lombok.Data;

import java.util.Map;

@Data
public class MarkToMarket {
    private String bookName;
    private double tradePnl;
    private Double fees;
    private Double pnlNetFees;
    private Map<String, Double> positionMap;
    private Map<String, Double> mtmPositionMap;
    private Map<String, Double> feesPositionMap;
    private Map<String, Double> feesMtmPositionMap;
}
