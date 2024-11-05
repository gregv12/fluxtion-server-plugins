/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl.talos;

import lombok.Data;

@Data
public class BookingRequest {

    private String reqid;
    private String type;
    private String seq;
    private String ts;
    private TradeDetails[] data;

}
