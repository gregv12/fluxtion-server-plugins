/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.cache;

import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class JsonFileCacheTest {

    @Test
    public void load() throws Exception {
        PositionSnapshot positionSnapshot = PositionSnapshot.of(
                10,
                new InstrumentPosition(Instrument.INSTRUMENT_GBP, 100),
                new InstrumentPosition(Instrument.INSTRUMENT_USD, 100)
        );

        JsonFileCache cache = getJsonFileCache("src/test/data/test.json", true);
        cache.put("1", positionSnapshot);
//        cache.doWork();
    }

    @Test
    public void load2() throws Exception {
        final Map<String, Map<String, Double>> positionMap = new HashMap<>();
        Map<String, Double> hedgeMap = Map.of("USDT", 77404.0707, "AVAX-USDT", -3886.07);
        Map<String, Double> clientMap = Map.of("USDT", -78384.787778, "AVAX-USDT", 3910.865, "XRP", -100.0);
        positionMap.put("hedge-avax-bybit", hedgeMap);
        positionMap.put("client-avax-bybit", clientMap);

        final JsonFileCache cache = getJsonFileCache("src/test/data/position_map.json", true);
        cache.put("positionMap", positionMap);
//        cache.doWork();

        final Map<String, Map<String, Double>> positionMapSaved = cache.get("positionMap");
        Assertions.assertEquals(positionMapSaved, positionMap);

        Double btc_client_original = positionMap.get("hedge-avax-bybit").get("USDT");
        Double btc_client_saved = positionMapSaved.get("hedge-avax-bybit").get("USDT");
        Assertions.assertEquals(btc_client_original, btc_client_saved);

        //update cache
        Map<String, Double>  hedgeMapNew = Map.of("USDT", 24.0, "AVAX-USDT", -10.1, "XRP", 100.0);
        positionMap.put("hedge-avax-bybit", hedgeMapNew);
        cache.put("positionMap", positionMap);

        cache.put("positionMap", positionMap);
        Assertions.assertNotEquals(btc_client_saved, positionMap);

        final Map<String, Map<String, Double>> positionMapSaved2 = cache.get("positionMap");
        Assertions.assertEquals(positionMapSaved2, positionMap);

        btc_client_original = positionMap.get("hedge-avax-bybit").get("USDT");
        btc_client_saved = positionMapSaved2.get("hedge-avax-bybit").get("USDT");
        Assertions.assertEquals(btc_client_original, btc_client_saved);

        //load the cache from the file
        final JsonFileCache cacheLoad2 = getJsonFileCache("src/test/data/position_map.json", false);
        final Map<String, Map<String, Double>> positionMapSaved3 = cacheLoad2.get("positionMap");
        Assertions.assertEquals(positionMapSaved3, positionMapSaved2);
    }

    @NotNull
    private static JsonFileCache getJsonFileCache(String fileName, boolean deleteExistingFile) {
        File file = new File(fileName);
        if (file.exists() & deleteExistingFile) {
            boolean deleted = file.delete();
            System.out.println("Deleted existing file: " + deleted);
        }

        JsonFileCache cache = new JsonFileCache();
//        File file = new File(fileName);
//        System.out.println(file.getAbsolutePath());
//        System.out.println("exists:" + file.exists());
        cache.setFileName(fileName);
        cache.init();
        return cache;
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
