/*
 *
 *  * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 *  * SPDX-License-Identifier: AGPL-3.0-only
 *
 */

package com.fluxtion.server.lib.pnl;

import com.fluxtion.server.lib.pnl.refdata.Instrument;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TradeBatchDTO {

    private List<TradeDTO> trades = new ArrayList<>();
    private Instrument feeInstrument = Instrument.INSTRUMENT_USD;
    private double fee = 0;

    public void addTrade(TradeDTO trade) {
        trades.add(trade);
    }
}
