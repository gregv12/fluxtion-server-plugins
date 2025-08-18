package com.fluxtion.server.plugin.trading.component.mockvenue.mktdata;

import com.fluxtion.server.plugin.trading.service.marketdata.MarketDataBook;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MarketDataBookGeneratorTest {

    @Test
    void generateRandom_respectsRanges_andPrecision_andAlwaysPublishesWithProbability1() {
        MarketDataBookConfig cfg = new MarketDataBookConfig();
        cfg.setFeedName("FEED");
        cfg.setVenueName("VENUE");
        cfg.setSymbol("SYM");
        cfg.setPublishProbability(1.0);
        cfg.setMinPrice(100.0);
        cfg.setMaxPrice(101.0);
        cfg.setMinSpread(0.1);
        cfg.setMaxSpread(0.3);
        cfg.setMinVolume(10);
        cfg.setMaxVolume(20);
        cfg.setPrecisionDpsPrice(3);
        cfg.setPrecisionDpsVolume(2);

        for (int i = 0; i < 100; i++) {
            MarketDataBook book = MarketDataBookGenerator.generateRandom(cfg);
            assertNotNull(book, "With probability 1.0, book should always be generated");

            // Range checks based on mid-price
            assertTrue(book.getBidPrice() <= book.getAskPrice());
            double mid = (book.getBidPrice() + book.getAskPrice()) / 2.0;
            assertTrue(mid >= cfg.getMinPrice() - 1e-9);
            assertTrue(mid <= cfg.getMaxPrice() + 1e-9);

            assertTrue(book.getBidQuantity() >= cfg.getMinVolume() - 1e-9);
            assertTrue(book.getAskQuantity() <= cfg.getMaxVolume() + 1e-9);

            // Precision checks: price to 3 dps, volume to 2 dps
            assertEquals(rounded(book.getBidPrice(), 3), book.getBidPrice(), 1e-9);
            assertEquals(rounded(book.getAskPrice(), 3), book.getAskPrice(), 1e-9);
            assertEquals(rounded(book.getBidQuantity(), 2), book.getBidQuantity(), 1e-9);
            assertEquals(rounded(book.getAskQuantity(), 2), book.getAskQuantity(), 1e-9);

            // Fields propagate
            assertEquals("FEED", book.getFeedName());
            assertEquals("VENUE", book.getVenueName());
            assertEquals("SYM", book.getSymbol());
        }
    }

    private static double rounded(double v, int places) {
        double s = Math.pow(10, places);
        return Math.round(v * s) / s;
    }
}
