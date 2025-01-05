/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl;

import lombok.Data;

import java.util.ArrayList;
import java.util.Collection;

@Data
public class PositionSnapshot {

    public static PositionSnapshot of(InstrumentPosition... positions) {
        PositionSnapshot positionSnapshot = new PositionSnapshot();
        for (InstrumentPosition position : positions) {
            positionSnapshot.getPositions().add(position);
        }
        return positionSnapshot;
    }

    private Collection<InstrumentPosition> positions = new ArrayList<>();
    private Collection<InstrumentPosition> feePositions = new ArrayList<>();
}
