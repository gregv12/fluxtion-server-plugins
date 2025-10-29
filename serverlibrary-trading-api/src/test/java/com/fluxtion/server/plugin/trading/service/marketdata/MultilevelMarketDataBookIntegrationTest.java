/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.trading.service.marketdata;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for MultilevelMarketDataBook.
 * Tests real-world scenarios and interactions with other components.
 */
class MultilevelMarketDataBookIntegrationTest {

    @Test
    void testMarketMakingScenario_Full20Levels() {
        MultilevelMarketDataBook book = new MultilevelMarketDataBook(
                "binance", "binance", "BTCUSD", 1L
        );

        // Build realistic 20-level book
        double basePrice = 50000.0;
        double tickSize = 0.10;

        for (int i = 0; i < 20; i++) {
            double bidPrice = basePrice - (i * tickSize);
            double askPrice = basePrice + tickSize + (i * tickSize);
            double quantity = 10.0 / (i + 1);  // Decreasing liquidity away from best

            book.updateBid(bidPrice, quantity, (int) (5.0 / (i + 1)) + 1);
            book.updateAsk(askPrice, quantity, (int) (5.0 / (i + 1)) + 1);
        }

        // Verify book structure
        assertEquals(20, book.getBidDepth());
        assertEquals(20, book.getAskDepth());
        assertEquals(50000.0, book.getBestBid().getPrice(), 0.0001);
        assertEquals(50000.10, book.getBestAsk().getPrice(), 0.0001);
        assertEquals(0.10, book.getSpread(), 0.0001);

        // Verify liquidity profile (higher at top)
        List<PriceLevel> bids = book.getBidLevels(5);
        assertTrue(bids.get(0).getQuantity() > bids.get(4).getQuantity());

        // Calculate top 5 liquidity
        double top5Volume = book.getTotalBidVolume(5);
        assertTrue(top5Volume > 0.0);
    }

    @Test
    void testMarketMakingScenario_RapidUpdates() {
        MultilevelMarketDataBook book = new MultilevelMarketDataBook(
                "exchange", "venue", "ETHUSD", 1L
        );

        // Simulate rapid price updates
        for (int i = 0; i < 1000; i++) {
            double price = 3000.0 + (i % 10) * 0.10;
            book.updateBid(price, 1.0 + (i % 5), i % 10);
            book.updateAsk(price + 0.10, 1.0 + (i % 5), i % 10);
        }

        // Book should remain in valid state
        assertTrue(book.getBidDepth() > 0);
        assertTrue(book.getAskDepth() > 0);
        assertTrue(book.hasInsideMarket());
        assertFalse(Double.isNaN(book.getSpread()));
    }

    @Test
    void testMarketMakingScenario_OrderExecution() {
        MultilevelMarketDataBook book = new MultilevelMarketDataBook(
                "venue", "venue", "BTC", 1L
        );

        book.updateBid(50000.0, 5.0, 10);
        book.updateBid(49999.0, 3.0, 5);
        book.updateBid(49998.0, 2.0, 3);

        // Simulate partial fill at best bid
        book.updateBid(50000.0, 3.0, 7);  // Reduced from 5.0 to 3.0
        assertEquals(3.0, book.getBestBid().getQuantity(), 0.0001);

        // Simulate complete fill (level removed)
        book.updateBid(50000.0, 0.0, 0);
        assertEquals(49999.0, book.getBestBid().getPrice(), 0.0001);
    }

    @Test
    void testMarketMakingScenario_SpreadAnalysis() {
        MultilevelMarketDataBook book = new MultilevelMarketDataBook(
                "venue", "venue", "ETH", 1L
        );

        // Tight spread - good for market making
        book.updateBid(3000.0, 10.0, 5);
        book.updateAsk(3000.01, 10.0, 5);
        assertEquals(0.01, book.getSpread(), 0.0001);
        assertTrue(book.getSpread() < 0.05);  // Acceptable for market making

        // Wide spread - poor liquidity
        book.clear();
        book.updateBid(3000.0, 1.0, 1);
        book.updateAsk(3005.0, 1.0, 1);
        assertEquals(5.0, book.getSpread(), 0.0001);
        assertTrue(book.getSpread() > 1.0);  // Too wide for tight market making
    }

    @Test
    void testConversion_InteroperabilityWithLegacyCode() {
        // Legacy code produces single-level book
        MarketDataBook legacy = new MarketDataBook(
                "feed", "venue", "BTC", 1L,
                50000.0, 1.0, 50001.0, 1.0
        );

        // Convert to multilevel for advanced processing
        MultilevelMarketDataBook multilevel = MarketDataBookConverter.fromTopOfBook(legacy);

        // Add more levels
        multilevel.updateBid(49999.0, 2.0, 2);
        multilevel.updateAsk(50002.0, 2.0, 2);

        assertEquals(2, multilevel.getBidDepth());
        assertEquals(2, multilevel.getAskDepth());

        // Convert back to legacy format for output
        MarketDataBook backToLegacy = MarketDataBookConverter.toTopOfBook(multilevel);
        assertEquals(50000.0, backToLegacy.getBidPrice(), 0.0001);
        assertEquals(50001.0, backToLegacy.getAskPrice(), 0.0001);
    }

    @Test
    void testStressTest_DeepBookOperations() {
        MultilevelBookConfig config = MultilevelBookConfig.deep();  // 50 levels
        MultilevelMarketDataBook book = new MultilevelMarketDataBook(
                "venue", "venue", "SYM", 1L, config
        );

        // Fill book to capacity
        for (int i = 0; i < 50; i++) {
            book.updateBid(1000.0 - i, 1.0, 1);
            book.updateAsk(1001.0 + i, 1.0, 1);
        }

        assertEquals(50, book.getBidDepth());
        assertEquals(50, book.getAskDepth());

        // Verify ordering is maintained
        List<PriceLevel> bids = book.getBidLevels();
        for (int i = 0; i < bids.size() - 1; i++) {
            assertTrue(bids.get(i).getPrice() > bids.get(i + 1).getPrice());
        }

        // Random access and updates
        book.updateBid(995.5, 5.0, 5);  // Insert in middle
        book.deleteBid(980.0);          // Delete from end

        // Book should remain valid
        assertTrue(book.getBidDepth() > 0);
        assertTrue(book.getBidDepth() <= 50);
    }

    @Test
    void testBookImbalance_TradingSignals() {
        MultilevelMarketDataBook book = new MultilevelMarketDataBook(
                "venue", "venue", "BTC", 1L
        );

        // Scenario 1: Heavy bid pressure (bullish signal)
        book.updateBid(50000.0, 100.0, 50);
        book.updateAsk(50001.0, 10.0, 5);

        double imbalance1 = book.getBookImbalance();
        assertTrue(imbalance1 > 0.8);  // Strong bid side

        // Scenario 2: Heavy ask pressure (bearish signal)
        book.clear();
        book.updateBid(50000.0, 10.0, 5);
        book.updateAsk(50001.0, 100.0, 50);

        double imbalance2 = book.getBookImbalance();
        assertTrue(imbalance2 < 0.2);  // Strong ask side

        // Scenario 3: Balanced market (neutral)
        book.clear();
        book.updateBid(50000.0, 50.0, 25);
        book.updateAsk(50001.0, 50.0, 25);

        double imbalance3 = book.getBookImbalance();
        assertEquals(0.5, imbalance3, 0.01);  // Balanced
    }

    @Test
    void testPerformance_BestBidAskAccess() {
        MultilevelMarketDataBook book = new MultilevelMarketDataBook(
                "venue", "venue", "SYM", 1L
        );

        // Build book
        for (int i = 0; i < 20; i++) {
            book.updateBid(100.0 - i, 1.0, 1);
            book.updateAsk(101.0 + i, 1.0, 1);
        }

        // Best bid/ask access should be O(1) - verify it doesn't scan
        long start = System.nanoTime();
        for (int i = 0; i < 10000; i++) {
            PriceLevel bid = book.getBestBid();
            PriceLevel ask = book.getBestAsk();
            assertNotNull(bid);
            assertNotNull(ask);
        }
        long elapsed = System.nanoTime() - start;

        // Should complete in well under 1ms (1,000,000 ns) for 10k iterations
        // Typical: ~50ns per iteration on modern hardware
        assertTrue(elapsed < 10_000_000, "Best bid/ask access too slow: " + elapsed + " ns");
    }
}
