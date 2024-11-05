/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl.calculator;


import com.fluxtion.runtime.dataflow.groupby.GroupBy;
import com.fluxtion.server.lib.pnl.refdata.Instrument;
import lombok.Data;

@Data
public class DerivedRateNode {

    public GroupBy<Instrument, Double> addDerived(
            GroupBy<Instrument, Double> rateMap,
            GroupBy<Instrument, Double> positionMap) {

        rateMap.toMap().put(new Instrument("MOCA"), 0.5);
        return rateMap;
    }
}
