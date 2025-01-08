/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl;

import com.fluxtion.runtime.annotations.builder.FluxtionIgnore;
import com.fluxtion.server.lib.pnl.refdata.Instrument;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Data
public class PositionSnapshot {

    public static PositionSnapshot of(InstrumentPosition... positions) {
        PositionSnapshot positionSnapshot = new PositionSnapshot();
        for (InstrumentPosition position : positions) {
            positionSnapshot.getPositions().add(position);
        }
        return positionSnapshot;
    }

    @FluxtionIgnore
    private Collection<InstrumentPosition> positions = new ArrayList<>();
    @FluxtionIgnore
    private Collection<InstrumentPosition> feePositions = new ArrayList<>();
    @FluxtionIgnore
    private Map<Instrument, InstrumentPositionSnapshot> instrumentPositionSnopshotMap = new HashMap<>();

    public Map<Instrument, InstrumentPosMtm> getInstrumentPositionMap() {
        Map<Instrument, InstrumentPosMtm> instrumentPositionMap = new HashMap<>();
        instrumentPositionSnopshotMap.forEach((instrument, positionSnapshot) -> {
            InstrumentPosMtm instrumentPosMtm = new InstrumentPosMtm();
            instrumentPosMtm.setBookName(instrument.instrumentName());

            positionSnapshot.getPositions().forEach((inst) -> {
                instrumentPosMtm.getPositionMap().put(inst.instrument(), inst.position());
            });

            positionSnapshot.feePositions.forEach((inst) -> {
                instrumentPosMtm.getPositionMap().put(inst.instrument(), inst.position());
            });

            instrumentPositionMap.put(instrument, instrumentPosMtm);
        });
        return instrumentPositionMap;
    }

    @Data
    public static class InstrumentPositionSnapshot {
        private Collection<InstrumentPosition> positions = new ArrayList<>();
        private Collection<InstrumentPosition> feePositions = new ArrayList<>();
    }
}
