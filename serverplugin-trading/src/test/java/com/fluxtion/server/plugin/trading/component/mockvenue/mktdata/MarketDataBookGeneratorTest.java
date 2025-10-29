package com.fluxtion.server.plugin.trading.component.mockvenue.mktdata;

import com.fluxtion.server.plugin.trading.service.marketdata.MarketDataBook;
import com.fluxtion.server.plugin.trading.service.marketdata.MultilevelMarketDataBook;
import com.fluxtion.server.plugin.trading.service.marketdata.MultilevelBookConfig;
import com.fluxtion.server.plugin.trading.service.marketdata.PriceLevel;
import org.junit.jupiter.api.Test;

import java.util.List;

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

    @Test
    void generateRandomMultilevel_respectsRanges_andGeneratesMultipleLevels() {
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

        int depth = 10;
        MultilevelBookConfig bookConfig = MultilevelBookConfig.builder()
                .maxDepth(depth)
                .build();

        for (int i = 0; i < 100; i++) {
            MultilevelMarketDataBook book = MarketDataBookGenerator.generateRandomMultilevel(cfg, depth, bookConfig);
            assertNotNull(book, "With probability 1.0, book should always be generated");

            // Verify depth
            assertEquals(depth, book.getBidDepth(), "Bid depth should match requested depth");
            assertEquals(depth, book.getAskDepth(), "Ask depth should match requested depth");

            // Verify best bid and ask exist
            assertNotNull(book.getBestBid());
            assertNotNull(book.getBestAsk());
            assertTrue(book.getBestBid().getPrice() < book.getBestAsk().getPrice(), "Best bid should be less than best ask");

            // Verify bid levels are in descending order
            List<PriceLevel> bidLevels = book.getBidLevels();
            for (int level = 0; level < bidLevels.size() - 1; level++) {
                assertTrue(bidLevels.get(level).getPrice() > bidLevels.get(level + 1).getPrice(),
                        "Bid levels should be in descending price order");
            }

            // Verify ask levels are in ascending order
            List<PriceLevel> askLevels = book.getAskLevels();
            for (int level = 0; level < askLevels.size() - 1; level++) {
                assertTrue(askLevels.get(level).getPrice() < askLevels.get(level + 1).getPrice(),
                        "Ask levels should be in ascending price order");
            }

            // Verify liquidity decreases with depth (on average)
            if (book.getBidDepth() >= 2) {
                assertTrue(bidLevels.get(0).getQuantity() >= bidLevels.get(bidLevels.size() - 1).getQuantity() * 0.5,
                        "Top of book should have more liquidity");
            }

            // Fields propagate
            assertEquals("FEED", book.getFeedName());
            assertEquals("VENUE", book.getVenueName());
            assertEquals("SYM", book.getSymbol());
        }
    }

    @Test
    void generateRandomMultilevel_withDefaultDepth_generates20Levels() {
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

        MultilevelMarketDataBook book = MarketDataBookGenerator.generateRandomMultilevel(cfg);
        assertNotNull(book);
        assertEquals(20, book.getBidDepth(), "Default depth should be 20");
        assertEquals(20, book.getAskDepth(), "Default depth should be 20");
    }

    private static double rounded(double v, int places) {
        double s = Math.pow(10, places);
        return Math.round(v * s) / s;
    }
}
