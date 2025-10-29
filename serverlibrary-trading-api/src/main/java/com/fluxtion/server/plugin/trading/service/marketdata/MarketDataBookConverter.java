/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.trading.service.marketdata;

/**
 * Utility for converting between single-level and multilevel market data books.
 *
 * <p>Provides bidirectional conversion allowing gradual migration from top-of-book
 * to multilevel book structures, and interoperability between old and new code.</p>
 *
 * @see MarketDataBook
 * @see MultilevelMarketDataBook
 */
public final class MarketDataBookConverter {

    private MarketDataBookConverter() {
        // Utility class
    }

    /**
     * Converts multilevel book to top-of-book only (extracts best bid/ask).
     *
     * <p>This is useful for backward compatibility when interfacing with code
     * that only expects single-level books.</p>
     *
     * @param multilevel the multilevel book to convert
     * @return single-level book with only best bid/ask, or empty book if input is empty
     * @throws IllegalArgumentException if multilevel is null
     */
    public static MarketDataBook toTopOfBook(MultilevelMarketDataBook multilevel) {
        if (multilevel == null) {
            throw new IllegalArgumentException("Multilevel book cannot be null");
        }

        if (multilevel.isEmpty()) {
            return new MarketDataBook(multilevel.getSymbol(), multilevel.getId());
        }

        PriceLevel bestBid = multilevel.getBestBid();
        PriceLevel bestAsk = multilevel.getBestAsk();

        // Handle case where only one side has liquidity
        double bidPrice = bestBid != null ? bestBid.getPrice() : 0.0;
        double bidQuantity = bestBid != null ? bestBid.getQuantity() : 0.0;
        int bidOrderCount = bestBid != null ? bestBid.getOrderCount() : 0;

        double askPrice = bestAsk != null ? bestAsk.getPrice() : 0.0;
        double askQuantity = bestAsk != null ? bestAsk.getQuantity() : 0.0;
        int askOrderCount = bestAsk != null ? bestAsk.getOrderCount() : 0;

        MarketDataBook book = new MarketDataBook(
                multilevel.getFeedName(),
                multilevel.getVenueName(),
                multilevel.getSymbol(),
                multilevel.getId(),
                bidPrice,
                bidQuantity,
                askPrice,
                askQuantity
        );
        book.setBidOrderCount(bidOrderCount);
        book.setAskOrderCount(askOrderCount);

        return book;
    }

    /**
     * Converts top-of-book to single-level multilevel book.
     *
     * <p>Creates a multilevel book with at most one level on each side,
     * using the best bid/ask from the single-level book.</p>
     *
     * @param book the single-level book to convert
     * @return multilevel book with one level per side (if quantity > 0)
     * @throws IllegalArgumentException if book is null
     */
    public static MultilevelMarketDataBook fromTopOfBook(MarketDataBook book) {
        if (book == null) {
            throw new IllegalArgumentException("Book cannot be null");
        }

        MultilevelMarketDataBook multilevel = new MultilevelMarketDataBook(
                book.getFeedName(),
                book.getVenueName(),
                book.getSymbol(),
                book.getId(),
                MultilevelBookConfig.defaultConfig()
        );

        // Add bid if present
        if (book.getBidPrice() > 0.0 && book.getBidQuantity() > 0.0) {
            multilevel.updateBid(
                    book.getBidPrice(),
                    book.getBidQuantity(),
                    book.getBidOrderCount()
            );
        }

        // Add ask if present
        if (book.getAskPrice() > 0.0 && book.getAskQuantity() > 0.0) {
            multilevel.updateAsk(
                    book.getAskPrice(),
                    book.getAskQuantity(),
                    book.getAskOrderCount()
            );
        }

        return multilevel;
    }

    /**
     * Converts top-of-book to multilevel book with custom configuration.
     *
     * @param book the single-level book to convert
     * @param config the configuration for the multilevel book
     * @return multilevel book with one level per side
     * @throws IllegalArgumentException if book or config is null
     */
    public static MultilevelMarketDataBook fromTopOfBook(MarketDataBook book,
                                                          MultilevelBookConfig config) {
        if (book == null) {
            throw new IllegalArgumentException("Book cannot be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("Config cannot be null");
        }

        MultilevelMarketDataBook multilevel = new MultilevelMarketDataBook(
                book.getFeedName(),
                book.getVenueName(),
                book.getSymbol(),
                book.getId(),
                config
        );

        // Add bid if present
        if (book.getBidPrice() > 0.0 && book.getBidQuantity() > 0.0) {
            multilevel.updateBid(
                    book.getBidPrice(),
                    book.getBidQuantity(),
                    book.getBidOrderCount()
            );
        }

        // Add ask if present
        if (book.getAskPrice() > 0.0 && book.getAskQuantity() > 0.0) {
            multilevel.updateAsk(
                    book.getAskPrice(),
                    book.getAskQuantity(),
                    book.getAskOrderCount()
            );
        }

        return multilevel;
    }
}
