/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.trading.service.marketdata;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for MultilevelMarketDataBook.
 */
class MultilevelMarketDataBookTest {

    private MultilevelMarketDataBook book;

    @BeforeEach
    void setUp() {
        book = new MultilevelMarketDataBook("feed1", "venue1", "BTCUSD", 1L);
    }

    // ==================== CONSTRUCTION TESTS ====================

    @Test
    void testEmptyBookCreation() {
        assertEquals("feed1", book.getFeedName());
        assertEquals("venue1", book.getVenueName());
        assertEquals("BTCUSD", book.getSymbol());
        assertEquals(1L, book.getId());
        assertEquals(0, book.getBidDepth());
        assertEquals(0, book.getAskDepth());
        assertTrue(book.isEmpty());
        assertFalse(book.hasInsideMarket());
    }

    @Test
    void testBookWithCustomConfig() {
        MultilevelBookConfig config = MultilevelBookConfig.builder()
                .maxDepth(10)
                .validateLevels(false)
                .build();
        MultilevelMarketDataBook customBook = new MultilevelMarketDataBook(
                "feed", "venue", "ETH", 123L, config
        );

        assertEquals(config, customBook.getConfig());
        assertEquals(10, config.getMaxDepth());
        assertFalse(config.isValidateLevels());
    }

    @Test
    void testMetadataStorage() {
        book.setId(999L);
        assertEquals(999L, book.getId());
    }

    // ==================== LEVEL UPDATE TESTS ====================

    @Test
    void testUpdateBidLevel_Insert() {
        book.updateBid(100.0, 1.5, 3);

        assertEquals(1, book.getBidDepth());
        PriceLevel best = book.getBestBid();
        assertNotNull(best);
        assertEquals(100.0, best.getPrice(), 0.0001);
        assertEquals(1.5, best.getQuantity(), 0.0001);
        assertEquals(3, best.getOrderCount());
    }

    @Test
    void testUpdateBidLevel_Modify() {
        book.updateBid(100.0, 1.5, 3);
        book.updateBid(100.0, 2.0, 5);  // Update same price

        assertEquals(1, book.getBidDepth());
        PriceLevel best = book.getBestBid();
        assertEquals(2.0, best.getQuantity(), 0.0001);
        assertEquals(5, best.getOrderCount());
    }

    @Test
    void testUpdateBidLevel_Delete() {
        book.updateBid(100.0, 1.5, 3);
        book.updateBid(100.0, 0.0, 0);  // Zero quantity deletes

        assertEquals(0, book.getBidDepth());
        assertNull(book.getBestBid());
    }

    @Test
    void testUpdateBidLevel_ZeroQuantity() {
        book.updateBid(100.0, 1.0, 1);
        book.updateBid(99.0, 1.0, 1);

        book.updateBid(100.0, 0.0, 0);  // Delete top level

        assertEquals(1, book.getBidDepth());
        assertEquals(99.0, book.getBestBid().getPrice(), 0.0001);
    }

    @Test
    void testUpdateAskLevel_Insert() {
        book.updateAsk(101.0, 1.2, 2);

        assertEquals(1, book.getAskDepth());
        PriceLevel best = book.getBestAsk();
        assertNotNull(best);
        assertEquals(101.0, best.getPrice(), 0.0001);
        assertEquals(1.2, best.getQuantity(), 0.0001);
        assertEquals(2, best.getOrderCount());
    }

    @Test
    void testUpdateAskLevel_Modify() {
        book.updateAsk(101.0, 1.2, 2);
        book.updateAsk(101.0, 1.8, 4);

        assertEquals(1, book.getAskDepth());
        assertEquals(1.8, book.getBestAsk().getQuantity(), 0.0001);
        assertEquals(4, book.getBestAsk().getOrderCount());
    }

    @Test
    void testUpdateAskLevel_Delete() {
        book.updateAsk(101.0, 1.2, 2);
        book.updateAsk(101.0, 0.0, 0);

        assertEquals(0, book.getAskDepth());
        assertNull(book.getBestAsk());
    }

    // ==================== SORTING TESTS ====================

    @Test
    void testBidsSortedDescending() {
        book.updateBid(100.0, 1.0, 1);
        book.updateBid(99.0, 1.0, 1);
        book.updateBid(101.0, 1.0, 1);

        List<PriceLevel> bids = book.getBidLevels();
        assertEquals(3, bids.size());
        assertEquals(101.0, bids.get(0).getPrice(), 0.0001);  // highest first
        assertEquals(100.0, bids.get(1).getPrice(), 0.0001);
        assertEquals(99.0, bids.get(2).getPrice(), 0.0001);    // lowest last
    }

    @Test
    void testAsksSortedAscending() {
        book.updateAsk(101.0, 1.0, 1);
        book.updateAsk(102.0, 1.0, 1);
        book.updateAsk(100.5, 1.0, 1);

        List<PriceLevel> asks = book.getAskLevels();
        assertEquals(3, asks.size());
        assertEquals(100.5, asks.get(0).getPrice(), 0.0001);  // lowest first
        assertEquals(101.0, asks.get(1).getPrice(), 0.0001);
        assertEquals(102.0, asks.get(2).getPrice(), 0.0001);  // highest last
    }

    @Test
    void testInsertionMaintainsSorting() {
        // Insert in random order
        book.updateBid(100.0, 1.0, 1);
        book.updateBid(95.0, 1.0, 1);
        book.updateBid(105.0, 1.0, 1);
        book.updateBid(98.0, 1.0, 1);
        book.updateBid(102.0, 1.0, 1);

        List<PriceLevel> bids = book.getBidLevels();
        for (int i = 0; i < bids.size() - 1; i++) {
            assertTrue(bids.get(i).getPrice() > bids.get(i + 1).getPrice(),
                    "Bids should be sorted descending");
        }
    }

    @Test
    void testDeletionMaintainsSorting() {
        book.updateBid(100.0, 1.0, 1);
        book.updateBid(99.0, 1.0, 1);
        book.updateBid(101.0, 1.0, 1);

        book.deleteBid(100.0);  // Delete middle level

        List<PriceLevel> bids = book.getBidLevels();
        assertEquals(2, bids.size());
        assertEquals(101.0, bids.get(0).getPrice(), 0.0001);
        assertEquals(99.0, bids.get(1).getPrice(), 0.0001);
    }

    // ==================== EDGE CASES ====================

    @Test
    void testFullBook_DropsLowestPriority() {
        MultilevelBookConfig config = MultilevelBookConfig.builder()
                .maxDepth(3)
                .build();
        MultilevelMarketDataBook smallBook = new MultilevelMarketDataBook(
                "feed", "venue", "SYM", 1L, config
        );

        smallBook.updateBid(100.0, 1.0, 1);
        smallBook.updateBid(99.0, 1.0, 1);
        smallBook.updateBid(98.0, 1.0, 1);
        smallBook.updateBid(101.0, 1.0, 1);  // Should drop 98.0

        assertEquals(3, smallBook.getBidDepth());
        List<PriceLevel> bids = smallBook.getBidLevels();
        assertEquals(101.0, bids.get(0).getPrice(), 0.0001);
        assertEquals(100.0, bids.get(1).getPrice(), 0.0001);
        assertEquals(99.0, bids.get(2).getPrice(), 0.0001);
    }

    @Test
    void testEmptyBook_AllOperations() {
        assertNull(book.getBestBid());
        assertNull(book.getBestAsk());
        assertTrue(book.getBidLevels().isEmpty());
        assertTrue(book.getAskLevels().isEmpty());
        assertTrue(Double.isNaN(book.getSpread()));
        assertTrue(Double.isNaN(book.getMidPrice()));
        assertEquals(0.5, book.getBookImbalance(), 0.0001);  // neutral
    }

    @Test
    void testSingleLevel_BothSides() {
        book.updateBid(100.0, 1.0, 1);
        book.updateAsk(101.0, 1.0, 1);

        assertTrue(book.hasInsideMarket());
        assertEquals(1.0, book.getSpread(), 0.0001);
        assertEquals(100.5, book.getMidPrice(), 0.0001);
    }

    @Test
    void testClear_RemovesAllLevels() {
        book.updateBid(100.0, 1.0, 1);
        book.updateBid(99.0, 1.0, 1);
        book.updateAsk(101.0, 1.0, 1);
        book.updateAsk(102.0, 1.0, 1);

        book.clear();

        assertEquals(0, book.getBidDepth());
        assertEquals(0, book.getAskDepth());
        assertTrue(book.isEmpty());
    }

    @Test
    void testDuplicatePrice_Updates() {
        book.updateBid(100.0, 1.0, 1);
        book.updateBid(100.0, 2.0, 2);  // Same price, new quantity

        assertEquals(1, book.getBidDepth());
        assertEquals(2.0, book.getBestBid().getQuantity(), 0.0001);
    }

    @Test
    void testInvalidPrice_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
                book.updateBid(0.0, 1.0, 1)
        );
        assertThrows(IllegalArgumentException.class, () ->
                book.updateBid(-1.0, 1.0, 1)
        );
    }

    @Test
    void testInvalidQuantity_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
                book.updateBid(100.0, -1.0, 1)
        );
    }

    @Test
    void testInvalidOrderCount_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
                book.updateBid(100.0, 1.0, -1)
        );
    }

    // ==================== DERIVED METRICS TESTS ====================

    @Test
    void testSpread_ValidInsideMarket() {
        book.updateBid(100.0, 1.0, 1);
        book.updateAsk(101.0, 1.0, 1);

        assertEquals(1.0, book.getSpread(), 0.0001);
    }

    @Test
    void testSpread_EmptyBook_ReturnsNaN() {
        assertTrue(Double.isNaN(book.getSpread()));
    }

    @Test
    void testSpread_OnlyBids_ReturnsNaN() {
        book.updateBid(100.0, 1.0, 1);
        assertTrue(Double.isNaN(book.getSpread()));
    }

    @Test
    void testMidPrice_Calculation() {
        book.updateBid(100.0, 1.0, 1);
        book.updateAsk(102.0, 1.0, 1);

        assertEquals(101.0, book.getMidPrice(), 0.0001);
    }

    @Test
    void testBookImbalance_Range() {
        book.updateBid(100.0, 10.0, 1);  // 10 units bid
        book.updateAsk(101.0, 5.0, 1);   // 5 units ask

        double imbalance = book.getBookImbalance();
        assertEquals(10.0 / 15.0, imbalance, 0.0001);  // 0.667
        assertTrue(imbalance > 0.5);  // More bids
    }

    @Test
    void testBookImbalance_EqualSides() {
        book.updateBid(100.0, 10.0, 1);
        book.updateAsk(101.0, 10.0, 1);

        assertEquals(0.5, book.getBookImbalance(), 0.0001);  // balanced
    }

    @Test
    void testTotalVolume_AllLevels() {
        book.updateBid(100.0, 1.0, 1);
        book.updateBid(99.0, 2.0, 1);
        book.updateBid(98.0, 3.0, 1);

        assertEquals(6.0, book.getTotalBidVolume(), 0.0001);
    }

    @Test
    void testTotalVolume_TopNLevels() {
        book.updateBid(100.0, 1.0, 1);
        book.updateBid(99.0, 2.0, 1);
        book.updateBid(98.0, 3.0, 1);

        assertEquals(3.0, book.getTotalBidVolume(2), 0.0001);  // Top 2 only: 1 + 2
    }

    @Test
    void testGetBidLevels_Limit() {
        book.updateBid(100.0, 1.0, 1);
        book.updateBid(99.0, 1.0, 1);
        book.updateBid(98.0, 1.0, 1);

        List<PriceLevel> top2 = book.getBidLevels(2);
        assertEquals(2, top2.size());
        assertEquals(100.0, top2.get(0).getPrice(), 0.0001);
        assertEquals(99.0, top2.get(1).getPrice(), 0.0001);
    }

    @Test
    void testGetAskLevels_Limit() {
        book.updateAsk(101.0, 1.0, 1);
        book.updateAsk(102.0, 1.0, 1);
        book.updateAsk(103.0, 1.0, 1);

        List<PriceLevel> top2 = book.getAskLevels(2);
        assertEquals(2, top2.size());
        assertEquals(101.0, top2.get(0).getPrice(), 0.0001);
        assertEquals(102.0, top2.get(1).getPrice(), 0.0001);
    }

    // ==================== DELETE OPERATION TESTS ====================

    @Test
    void testDeleteBid_Success() {
        book.updateBid(100.0, 1.0, 1);
        book.updateBid(99.0, 1.0, 1);

        assertTrue(book.deleteBid(100.0));
        assertEquals(1, book.getBidDepth());
        assertEquals(99.0, book.getBestBid().getPrice(), 0.0001);
    }

    @Test
    void testDeleteBid_NotFound() {
        book.updateBid(100.0, 1.0, 1);

        assertFalse(book.deleteBid(99.0));  // Price doesn't exist
        assertEquals(1, book.getBidDepth());
    }

    @Test
    void testDeleteAsk_Success() {
        book.updateAsk(101.0, 1.0, 1);
        book.updateAsk(102.0, 1.0, 1);

        assertTrue(book.deleteAsk(101.0));
        assertEquals(1, book.getAskDepth());
        assertEquals(102.0, book.getBestAsk().getPrice(), 0.0001);
    }

    @Test
    void testDeleteAsk_NotFound() {
        book.updateAsk(101.0, 1.0, 1);

        assertFalse(book.deleteAsk(102.0));
        assertEquals(1, book.getAskDepth());
    }

    // ==================== IMMUTABILITY TESTS ====================

    @Test
    void testGetBidLevels_ReturnsUnmodifiableList() {
        book.updateBid(100.0, 1.0, 1);
        List<PriceLevel> bids = book.getBidLevels();

        assertThrows(UnsupportedOperationException.class, () ->
                bids.add(new PriceLevel(99.0, 1.0, 1))
        );
    }

    @Test
    void testGetAskLevels_ReturnsUnmodifiableList() {
        book.updateAsk(101.0, 1.0, 1);
        List<PriceLevel> asks = book.getAskLevels();

        assertThrows(UnsupportedOperationException.class, () ->
                asks.add(new PriceLevel(102.0, 1.0, 1))
        );
    }

    // ==================== MARKET MAKING SCENARIO TESTS ====================

    @Test
    void testMarketMakingScenario_DeepBook() {
        // Build a realistic 20-level book
        double basePrice = 50000.0;
        for (int i = 0; i < 20; i++) {
            book.updateBid(basePrice - i, 1.0 / (i + 1), i + 1);
            book.updateAsk(basePrice + 1 + i, 1.0 / (i + 1), i + 1);
        }

        assertEquals(20, book.getBidDepth());
        assertEquals(20, book.getAskDepth());
        assertEquals(1.0, book.getSpread(), 0.0001);
        assertEquals(50000.5, book.getMidPrice(), 0.0001);

        // Check liquidity at top 5 levels
        double top5BidVol = book.getTotalBidVolume(5);
        assertTrue(top5BidVol > 0.0);
    }

    @Test
    void testMarketMakingScenario_PriceUpdate() {
        book.updateBid(50000.0, 1.0, 5);
        book.updateAsk(50001.0, 1.0, 5);

        // Market moves - best bid improves
        book.updateBid(50000.5, 2.0, 10);

        assertEquals(50000.5, book.getBestBid().getPrice(), 0.0001);
        assertEquals(2, book.getBidDepth());
        assertEquals(0.5, book.getSpread(), 0.0001);
    }

    @Test
    void testMarketMakingScenario_LiquidityRemoval() {
        book.updateBid(50000.0, 1.0, 5);
        book.updateBid(49999.0, 2.0, 10);
        book.updateAsk(50001.0, 1.0, 5);

        // Top bid gets filled (removed)
        book.updateBid(50000.0, 0.0, 0);

        assertEquals(49999.0, book.getBestBid().getPrice(), 0.0001);
        assertEquals(2.0, book.getSpread(), 0.0001);
    }

    // ==================== AVERAGE PRICE FOR QUANTITY TESTS ====================

    @Test
    void testGetAverageBidPriceForQuantity_SingleLevel() {
        book.updateBid(100.0, 5.0, 1);

        // Quantity within single level
        double avgPrice = book.getAverageBidPriceForQuantity(3.0);
        assertEquals(100.0, avgPrice, 0.0001);
    }

    @Test
    void testGetAverageBidPriceForQuantity_MultipleLevels() {
        book.updateBid(100.0, 2.0, 1);
        book.updateBid(99.0, 3.0, 1);
        book.updateBid(98.0, 2.0, 1);

        // Need to go through multiple levels: 2@100 + 1@99 = 3 units
        // VWAP = (100*2 + 99*1) / 3 = 299/3 = 99.667
        double avgPrice = book.getAverageBidPriceForQuantity(3.0);
        assertEquals(99.6667, avgPrice, 0.0001);
    }

    @Test
    void testGetAverageBidPriceForQuantity_PartialLastLevel() {
        book.updateBid(100.0, 1.0, 1);
        book.updateBid(99.0, 5.0, 1);

        // Take all of level 1 (1@100) and part of level 2 (1.5@99)
        // VWAP = (100*1 + 99*1.5) / 2.5 = 248.5/2.5 = 99.4
        double avgPrice = book.getAverageBidPriceForQuantity(2.5);
        assertEquals(99.4, avgPrice, 0.0001);
    }

    @Test
    void testGetAverageBidPriceForQuantity_InsufficientLiquidity() {
        book.updateBid(100.0, 2.0, 1);
        book.updateBid(99.0, 1.0, 1);

        // Request more than available (only 3.0 available)
        // Should return average of all available
        double avgPrice = book.getAverageBidPriceForQuantity(5.0);
        // VWAP = (100*2 + 99*1) / 3 = 299/3 = 99.667
        assertEquals(99.6667, avgPrice, 0.0001);
    }

    @Test
    void testGetAverageBidPriceForQuantity_EmptyBook() {
        assertTrue(Double.isNaN(book.getAverageBidPriceForQuantity(1.0)));
    }

    @Test
    void testGetAverageBidPriceForQuantity_InvalidQuantity() {
        book.updateBid(100.0, 1.0, 1);

        assertTrue(Double.isNaN(book.getAverageBidPriceForQuantity(0.0)));
        assertTrue(Double.isNaN(book.getAverageBidPriceForQuantity(-1.0)));
    }

    @Test
    void testGetAverageAskPriceForQuantity_SingleLevel() {
        book.updateAsk(101.0, 5.0, 1);

        // Quantity within single level
        double avgPrice = book.getAverageAskPriceForQuantity(3.0);
        assertEquals(101.0, avgPrice, 0.0001);
    }

    @Test
    void testGetAverageAskPriceForQuantity_MultipleLevels() {
        book.updateAsk(101.0, 2.0, 1);
        book.updateAsk(102.0, 3.0, 1);
        book.updateAsk(103.0, 2.0, 1);

        // Need to go through multiple levels: 2@101 + 1@102 = 3 units
        // VWAP = (101*2 + 102*1) / 3 = 304/3 = 101.333
        double avgPrice = book.getAverageAskPriceForQuantity(3.0);
        assertEquals(101.3333, avgPrice, 0.0001);
    }

    @Test
    void testGetAverageAskPriceForQuantity_PartialLastLevel() {
        book.updateAsk(101.0, 1.0, 1);
        book.updateAsk(102.0, 5.0, 1);

        // Take all of level 1 (1@101) and part of level 2 (1.5@102)
        // VWAP = (101*1 + 102*1.5) / 2.5 = 254/2.5 = 101.6
        double avgPrice = book.getAverageAskPriceForQuantity(2.5);
        assertEquals(101.6, avgPrice, 0.0001);
    }

    @Test
    void testGetAverageAskPriceForQuantity_InsufficientLiquidity() {
        book.updateAsk(101.0, 2.0, 1);
        book.updateAsk(102.0, 1.0, 1);

        // Request more than available (only 3.0 available)
        double avgPrice = book.getAverageAskPriceForQuantity(5.0);
        // VWAP = (101*2 + 102*1) / 3 = 304/3 = 101.333
        assertEquals(101.3333, avgPrice, 0.0001);
    }

    @Test
    void testGetAverageAskPriceForQuantity_EmptyBook() {
        assertTrue(Double.isNaN(book.getAverageAskPriceForQuantity(1.0)));
    }

    // ==================== WORST PRICE FOR QUANTITY TESTS ====================

    @Test
    void testGetWorstBidPriceForQuantity_SingleLevel() {
        book.updateBid(100.0, 5.0, 1);

        // Quantity within single level
        double worstPrice = book.getWorstBidPriceForQuantity(3.0);
        assertEquals(100.0, worstPrice, 0.0001);
    }

    @Test
    void testGetWorstBidPriceForQuantity_MultipleLevels() {
        book.updateBid(100.0, 2.0, 1);
        book.updateBid(99.0, 3.0, 1);
        book.updateBid(98.0, 2.0, 1);

        // Need 2nd level: 2@100 exhausted, finish at 99
        double worstPrice = book.getWorstBidPriceForQuantity(3.0);
        assertEquals(99.0, worstPrice, 0.0001);
    }

    @Test
    void testGetWorstBidPriceForQuantity_PartialLastLevel() {
        book.updateBid(100.0, 1.0, 1);
        book.updateBid(99.0, 5.0, 1);

        // Take all of level 1, partial of level 2 - worst is 99
        double worstPrice = book.getWorstBidPriceForQuantity(2.5);
        assertEquals(99.0, worstPrice, 0.0001);
    }

    @Test
    void testGetWorstBidPriceForQuantity_ExactlyFillsLevel() {
        book.updateBid(100.0, 2.0, 1);
        book.updateBid(99.0, 3.0, 1);

        // Exactly fills first level
        double worstPrice = book.getWorstBidPriceForQuantity(2.0);
        assertEquals(100.0, worstPrice, 0.0001);

        // Exactly fills both levels
        worstPrice = book.getWorstBidPriceForQuantity(5.0);
        assertEquals(99.0, worstPrice, 0.0001);
    }

    @Test
    void testGetWorstBidPriceForQuantity_InsufficientLiquidity() {
        book.updateBid(100.0, 2.0, 1);
        book.updateBid(99.0, 1.0, 1);

        // Request more than available - returns worst available price
        double worstPrice = book.getWorstBidPriceForQuantity(10.0);
        assertEquals(99.0, worstPrice, 0.0001);  // Last level price
    }

    @Test
    void testGetWorstBidPriceForQuantity_EmptyBook() {
        assertTrue(Double.isNaN(book.getWorstBidPriceForQuantity(1.0)));
    }

    @Test
    void testGetWorstBidPriceForQuantity_InvalidQuantity() {
        book.updateBid(100.0, 1.0, 1);

        assertTrue(Double.isNaN(book.getWorstBidPriceForQuantity(0.0)));
        assertTrue(Double.isNaN(book.getWorstBidPriceForQuantity(-1.0)));
    }

    @Test
    void testGetWorstAskPriceForQuantity_SingleLevel() {
        book.updateAsk(101.0, 5.0, 1);

        // Quantity within single level
        double worstPrice = book.getWorstAskPriceForQuantity(3.0);
        assertEquals(101.0, worstPrice, 0.0001);
    }

    @Test
    void testGetWorstAskPriceForQuantity_MultipleLevels() {
        book.updateAsk(101.0, 2.0, 1);
        book.updateAsk(102.0, 3.0, 1);
        book.updateAsk(103.0, 2.0, 1);

        // Need 2nd level: 2@101 exhausted, finish at 102
        double worstPrice = book.getWorstAskPriceForQuantity(3.0);
        assertEquals(102.0, worstPrice, 0.0001);
    }

    @Test
    void testGetWorstAskPriceForQuantity_PartialLastLevel() {
        book.updateAsk(101.0, 1.0, 1);
        book.updateAsk(102.0, 5.0, 1);

        // Take all of level 1, partial of level 2 - worst is 102
        double worstPrice = book.getWorstAskPriceForQuantity(2.5);
        assertEquals(102.0, worstPrice, 0.0001);
    }

    @Test
    void testGetWorstAskPriceForQuantity_InsufficientLiquidity() {
        book.updateAsk(101.0, 2.0, 1);
        book.updateAsk(102.0, 1.0, 1);

        // Request more than available - returns worst available price
        double worstPrice = book.getWorstAskPriceForQuantity(10.0);
        assertEquals(102.0, worstPrice, 0.0001);  // Last level price
    }

    @Test
    void testGetWorstAskPriceForQuantity_EmptyBook() {
        assertTrue(Double.isNaN(book.getWorstAskPriceForQuantity(1.0)));
    }

    // ==================== MARKET MAKING USE CASE TESTS ====================

    @Test
    void testSlippageCalculation() {
        // Build a realistic book with increasing spread
        book.updateBid(100.0, 1.0, 1);
        book.updateBid(99.9, 2.0, 2);
        book.updateBid(99.8, 3.0, 3);

        book.updateAsk(100.1, 1.0, 1);
        book.updateAsk(100.2, 2.0, 2);
        book.updateAsk(100.3, 3.0, 3);

        // Small order - no slippage
        double small = book.getAverageBidPriceForQuantity(0.5);
        assertEquals(100.0, small, 0.0001);

        // Medium order - some slippage
        double medium = book.getAverageBidPriceForQuantity(2.0);
        // (100*1 + 99.9*1) / 2 = 99.95
        assertEquals(99.95, medium, 0.0001);

        // Large order - significant slippage
        double large = book.getAverageBidPriceForQuantity(5.0);
        // (100*1 + 99.9*2 + 99.8*2) / 5 = 99.88
        assertEquals(99.88, large, 0.0001);

        // Worst price shows maximum slippage
        double worstLarge = book.getWorstBidPriceForQuantity(5.0);
        assertEquals(99.8, worstLarge, 0.0001);
    }

    @Test
    void testMarketImpactEstimation() {
        // Tight market
        book.updateBid(100.0, 10.0, 5);
        book.updateAsk(100.1, 10.0, 5);

        // Small quantity - minimal impact
        double avgBid = book.getAverageBidPriceForQuantity(5.0);
        double avgAsk = book.getAverageAskPriceForQuantity(5.0);
        assertEquals(100.0, avgBid, 0.0001);
        assertEquals(100.1, avgAsk, 0.0001);

        // Market impact = average - best
        double bidImpact = book.getBestBid().getPrice() - avgBid;
        double askImpact = avgAsk - book.getBestAsk().getPrice();
        assertEquals(0.0, bidImpact, 0.0001);  // No impact for small order
        assertEquals(0.0, askImpact, 0.0001);
    }

    @Test
    void testOrderSizingBasedOnLiquidity() {
        // Build book with limited liquidity
        book.updateBid(100.0, 1.0, 1);
        book.updateBid(99.0, 1.0, 1);
        book.updateBid(98.0, 1.0, 1);
        book.updateBid(97.0, 1.0, 1);

        // Determine max size for acceptable slippage (e.g., 1%)
        double bestBid = book.getBestBid().getPrice();

        // Test different sizes
        double size1 = 1.0;
        double avg1 = book.getAverageBidPriceForQuantity(size1);
        double slippage1 = (bestBid - avg1) / bestBid * 100;
        assertEquals(0.0, slippage1, 0.01);  // No slippage for single best level

        double size2 = 2.0;
        double avg2 = book.getAverageBidPriceForQuantity(size2);
        double slippage2 = (bestBid - avg2) / bestBid * 100;
        // avg2 = (100*1 + 99*1) / 2 = 99.5, slippage = 0.5%
        assertEquals(0.5, slippage2, 0.01);  // Still acceptable

        double size3 = 3.0;
        double avg3 = book.getAverageBidPriceForQuantity(size3);
        double slippage3 = (bestBid - avg3) / bestBid * 100;
        // avg3 = (100*1 + 99*1 + 98*1) / 3 = 99.0, slippage = 1.0%
        assertEquals(1.0, slippage3, 0.01);  // At threshold

        double size4 = 4.0;
        double avg4 = book.getAverageBidPriceForQuantity(size4);
        double slippage4 = (bestBid - avg4) / bestBid * 100;
        // avg4 = (100*1 + 99*1 + 98*1 + 97*1) / 4 = 98.5, slippage = 1.5%
        assertTrue(slippage4 > 1.0);  // Exceeds 1% threshold
    }
}
