/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.trading.service.marketdata;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MarketDataBookConverter conversion utilities.
 */
class MarketDataBookConverterTest {

    @Test
    void testToTopOfBook_PreservesMetadata() {
        MultilevelMarketDataBook multilevel = new MultilevelMarketDataBook(
                "feed1", "venue1", "BTCUSD", 123L
        );
        multilevel.updateBid(100.0, 1.5, 3);
        multilevel.updateAsk(101.0, 1.2, 2);

        MarketDataBook topOfBook = MarketDataBookConverter.toTopOfBook(multilevel);

        assertEquals("feed1", topOfBook.getFeedName());
        assertEquals("venue1", topOfBook.getVenueName());
        assertEquals("BTCUSD", topOfBook.getSymbol());
        assertEquals(123L, topOfBook.getId());
        assertEquals(100.0, topOfBook.getBidPrice(), 0.0001);
        assertEquals(1.5, topOfBook.getBidQuantity(), 0.0001);
        assertEquals(3, topOfBook.getBidOrderCount());
        assertEquals(101.0, topOfBook.getAskPrice(), 0.0001);
        assertEquals(1.2, topOfBook.getAskQuantity(), 0.0001);
        assertEquals(2, topOfBook.getAskOrderCount());
    }

    @Test
    void testToTopOfBook_EmptyBook() {
        MultilevelMarketDataBook multilevel = new MultilevelMarketDataBook(
                "feed1", "venue1", "ETHUSD", 456L
        );

        MarketDataBook topOfBook = MarketDataBookConverter.toTopOfBook(multilevel);

        assertEquals("ETHUSD", topOfBook.getSymbol());
        assertEquals(456L, topOfBook.getId());
    }

    @Test
    void testToTopOfBook_OnlyBids() {
        MultilevelMarketDataBook multilevel = new MultilevelMarketDataBook(
                "feed1", "venue1", "SYM", 1L
        );
        multilevel.updateBid(100.0, 1.0, 1);

        MarketDataBook topOfBook = MarketDataBookConverter.toTopOfBook(multilevel);

        assertEquals(100.0, topOfBook.getBidPrice(), 0.0001);
        assertEquals(1.0, topOfBook.getBidQuantity(), 0.0001);
        assertEquals(0.0, topOfBook.getAskPrice(), 0.0001);
        assertEquals(0.0, topOfBook.getAskQuantity(), 0.0001);
    }

    @Test
    void testToTopOfBook_OnlyAsks() {
        MultilevelMarketDataBook multilevel = new MultilevelMarketDataBook(
                "feed1", "venue1", "SYM", 1L
        );
        multilevel.updateAsk(101.0, 1.0, 1);

        MarketDataBook topOfBook = MarketDataBookConverter.toTopOfBook(multilevel);

        assertEquals(0.0, topOfBook.getBidPrice(), 0.0001);
        assertEquals(0.0, topOfBook.getBidQuantity(), 0.0001);
        assertEquals(101.0, topOfBook.getAskPrice(), 0.0001);
        assertEquals(1.0, topOfBook.getAskQuantity(), 0.0001);
    }

    @Test
    void testToTopOfBook_MultipleLevels_ExtractsBest() {
        MultilevelMarketDataBook multilevel = new MultilevelMarketDataBook(
                "feed1", "venue1", "SYM", 1L
        );
        multilevel.updateBid(100.0, 1.0, 1);
        multilevel.updateBid(99.0, 2.0, 2);
        multilevel.updateAsk(101.0, 1.5, 1);
        multilevel.updateAsk(102.0, 2.5, 2);

        MarketDataBook topOfBook = MarketDataBookConverter.toTopOfBook(multilevel);

        // Should extract best bid (100.0) and best ask (101.0)
        assertEquals(100.0, topOfBook.getBidPrice(), 0.0001);
        assertEquals(101.0, topOfBook.getAskPrice(), 0.0001);
    }

    @Test
    void testToTopOfBook_NullInput() {
        assertThrows(IllegalArgumentException.class, () ->
                MarketDataBookConverter.toTopOfBook(null)
        );
    }

    @Test
    void testFromTopOfBook_CreatesSingleLevel() {
        MarketDataBook book = new MarketDataBook(
                "feed1", "venue1", "BTCUSD", 789L,
                100.0, 1.5, 101.0, 1.2
        );
        book.setBidOrderCount(3);
        book.setAskOrderCount(2);

        MultilevelMarketDataBook multilevel = MarketDataBookConverter.fromTopOfBook(book);

        assertEquals("feed1", multilevel.getFeedName());
        assertEquals("venue1", multilevel.getVenueName());
        assertEquals("BTCUSD", multilevel.getSymbol());
        assertEquals(789L, multilevel.getId());
        assertEquals(1, multilevel.getBidDepth());
        assertEquals(1, multilevel.getAskDepth());

        PriceLevel bestBid = multilevel.getBestBid();
        assertEquals(100.0, bestBid.getPrice(), 0.0001);
        assertEquals(1.5, bestBid.getQuantity(), 0.0001);
        assertEquals(3, bestBid.getOrderCount());

        PriceLevel bestAsk = multilevel.getBestAsk();
        assertEquals(101.0, bestAsk.getPrice(), 0.0001);
        assertEquals(1.2, bestAsk.getQuantity(), 0.0001);
        assertEquals(2, bestAsk.getOrderCount());
    }

    @Test
    void testFromTopOfBook_EmptyBook() {
        MarketDataBook book = new MarketDataBook("SYM", 1L);

        MultilevelMarketDataBook multilevel = MarketDataBookConverter.fromTopOfBook(book);

        assertEquals(0, multilevel.getBidDepth());
        assertEquals(0, multilevel.getAskDepth());
        assertTrue(multilevel.isEmpty());
    }

    @Test
    void testFromTopOfBook_WithCustomConfig() {
        MarketDataBook book = new MarketDataBook(
                "feed1", "venue1", "ETH", 1L,
                100.0, 1.0, 101.0, 1.0
        );
        MultilevelBookConfig config = MultilevelBookConfig.builder()
                .maxDepth(10)
                .build();

        MultilevelMarketDataBook multilevel = MarketDataBookConverter.fromTopOfBook(book, config);

        assertEquals(10, multilevel.getConfig().getMaxDepth());
        assertEquals(1, multilevel.getBidDepth());
    }

    @Test
    void testFromTopOfBook_NullInput() {
        assertThrows(IllegalArgumentException.class, () ->
                MarketDataBookConverter.fromTopOfBook(null)
        );
    }

    @Test
    void testFromTopOfBook_NullConfig() {
        MarketDataBook book = new MarketDataBook("SYM", 1L);

        assertThrows(IllegalArgumentException.class, () ->
                MarketDataBookConverter.fromTopOfBook(book, null)
        );
    }

    @Test
    void testRoundTrip_Conversion() {
        // Start with multilevel
        MultilevelMarketDataBook original = new MultilevelMarketDataBook(
                "feed", "venue", "SYM", 1L
        );
        original.updateBid(100.0, 1.0, 1);
        original.updateAsk(101.0, 1.0, 1);

        // Convert to top-of-book
        MarketDataBook topOfBook = MarketDataBookConverter.toTopOfBook(original);

        // Convert back to multilevel
        MultilevelMarketDataBook roundTrip = MarketDataBookConverter.fromTopOfBook(topOfBook);

        // Should preserve best bid/ask
        assertEquals(original.getBestBid().getPrice(), roundTrip.getBestBid().getPrice(), 0.0001);
        assertEquals(original.getBestAsk().getPrice(), roundTrip.getBestAsk().getPrice(), 0.0001);
    }
}
