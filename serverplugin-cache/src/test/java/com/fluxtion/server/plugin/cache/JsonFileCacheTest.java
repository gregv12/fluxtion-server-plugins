/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.cache;

import lombok.Data;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

public class JsonFileCacheTest {

    @Test
    public void load() throws Exception {
        JsonFileCache cache = new JsonFileCache();
        String fileName = "src/test/data/test.json";
        File file = new File(fileName);
        System.out.println(file.getAbsolutePath());
        System.out.println("exists:" + file.exists());
        cache.setFileName(fileName);
        cache.init();

        PositionSnapshot positionSnapshot = PositionSnapshot.of(
                10,
                new InstrumentPosition(Instrument.INSTRUMENT_GBP, 100),
                new InstrumentPosition(Instrument.INSTRUMENT_USD, 100)
        );

        cache.put("1", positionSnapshot);
        cache.doWork();
    }

    @Data
    public static class PositionSnapshot {

        public static PositionSnapshot of(long tradeId, InstrumentPosition... positions) {
            PositionSnapshot positionSnapshot = new PositionSnapshot();
            positionSnapshot.setTradeId(tradeId);
            for (InstrumentPosition position : positions) {
                positionSnapshot.getPositions().add(position);
            }
            return positionSnapshot;
        }

        private long tradeId;
        private Collection<InstrumentPosition> positions = new ArrayList<>();
    }

    public record InstrumentPosition(Instrument instrument, double position) {
    }

    public record Instrument(String instrumentName) {

        public static final Instrument INSTRUMENT_GBP = new Instrument("GBP");
        public static final Instrument INSTRUMENT_USD = new Instrument("USD");
        public static final Instrument INSTRUMENT_USDT = new Instrument("USDT");
    }

}
