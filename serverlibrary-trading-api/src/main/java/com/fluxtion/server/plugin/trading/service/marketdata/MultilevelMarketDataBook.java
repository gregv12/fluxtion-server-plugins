/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.trading.service.marketdata;

import lombok.Getter;
import lombok.ToString;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Multilevel market data book supporting configurable depth per side.
 *
 * <p>This implementation uses fixed-depth sorted arrays optimized for market making systems.
 * It provides O(1) access to best bid/ask and efficient updates for typical book depths
 * (5-50 levels).</p>
 *
 * <p><strong>Thread-safety:</strong> This class is NOT thread-safe. External synchronization
 * required if accessed from multiple threads.</p>
 *
 * <p><strong>Performance characteristics:</strong></p>
 * <ul>
 *   <li>getBestBid/Ask(): O(1)</li>
 *   <li>updateLevel(): O(log n) + O(n) for insertion/deletion</li>
 *   <li>Memory: ~80 bytes per level (20 levels ≈ 3.2KB per book)</li>
 * </ul>
 *
 * @see PriceLevel
 * @see MultilevelBookConfig
 */
@ToString(exclude = {"bidLevels", "askLevels"})
public final class MultilevelMarketDataBook implements MarketFeedEvent {

    // ==================== METADATA ====================

    @Getter
    private final String feedName;

    @Getter
    private final String venueName;

    @Getter
    private final String symbol;

    @Getter
    private long id;

    @Getter
    private final MultilevelBookConfig config;

    // ==================== BOOK STATE ====================

    private final PriceLevel[] bidLevels;  // sorted descending by price
    private final PriceLevel[] askLevels;  // sorted ascending by price
    private int bidDepth;  // actual number of levels
    private int askDepth;  // actual number of levels

    // ==================== CONSTRUCTORS ====================

    /**
     * Creates empty book with default configuration (20 levels for market making).
     *
     * @param feedName the feed name
     * @param venueName the venue name
     * @param symbol the symbol
     * @param id the book ID
     */
    public MultilevelMarketDataBook(String feedName, String venueName,
                                     String symbol, long id) {
        this(feedName, venueName, symbol, id, MultilevelBookConfig.defaultConfig());
    }

    /**
     * Creates empty book with custom configuration.
     *
     * @param feedName the feed name
     * @param venueName the venue name
     * @param symbol the symbol
     * @param id the book ID
     * @param config the book configuration
     */
    public MultilevelMarketDataBook(String feedName, String venueName,
                                     String symbol, long id,
                                     MultilevelBookConfig config) {
        config.validate();
        this.feedName = feedName;
        this.venueName = venueName;
        this.symbol = symbol;
        this.id = id;
        this.config = config;
        this.bidLevels = new PriceLevel[config.getMaxDepth()];
        this.askLevels = new PriceLevel[config.getMaxDepth()];
        this.bidDepth = 0;
        this.askDepth = 0;
    }

    // ==================== LEVEL ACCESS ====================

    /**
     * Gets the best bid (highest price).
     *
     * @return best bid level, or null if no bids
     * @complexity O(1)
     */
    public PriceLevel getBestBid() {
        return bidDepth > 0 ? bidLevels[0] : null;
    }

    /**
     * Gets the best ask (lowest price).
     *
     * @return best ask level, or null if no asks
     * @complexity O(1)
     */
    public PriceLevel getBestAsk() {
        return askDepth > 0 ? askLevels[0] : null;
    }

    /**
     * Gets all bid levels as immutable list.
     *
     * @return list of bid levels sorted descending by price
     * @complexity O(n) where n = actual depth
     */
    public List<PriceLevel> getBidLevels() {
        return Collections.unmodifiableList(
                Arrays.asList(Arrays.copyOf(bidLevels, bidDepth))
        );
    }

    /**
     * Gets all ask levels as immutable list.
     *
     * @return list of ask levels sorted ascending by price
     * @complexity O(n) where n = actual depth
     */
    public List<PriceLevel> getAskLevels() {
        return Collections.unmodifiableList(
                Arrays.asList(Arrays.copyOf(askLevels, askDepth))
        );
    }

    /**
     * Gets top N bid levels.
     *
     * @param levels number of levels to retrieve
     * @return list of up to N bid levels
     * @complexity O(min(n, levels))
     */
    public List<PriceLevel> getBidLevels(int levels) {
        int count = Math.min(levels, bidDepth);
        return Collections.unmodifiableList(
                Arrays.asList(Arrays.copyOf(bidLevels, count))
        );
    }

    /**
     * Gets top N ask levels.
     *
     * @param levels number of levels to retrieve
     * @return list of up to N ask levels
     * @complexity O(min(n, levels))
     */
    public List<PriceLevel> getAskLevels(int levels) {
        int count = Math.min(levels, askDepth);
        return Collections.unmodifiableList(
                Arrays.asList(Arrays.copyOf(askLevels, count))
        );
    }

    /**
     * Gets current bid depth (number of levels).
     *
     * @return number of bid levels
     */
    public int getBidDepth() {
        return bidDepth;
    }

    /**
     * Gets current ask depth (number of levels).
     *
     * @return number of ask levels
     */
    public int getAskDepth() {
        return askDepth;
    }

    /**
     * Checks if book is empty (no bids or asks).
     *
     * @return true if no levels on either side
     */
    public boolean isEmpty() {
        return bidDepth == 0 && askDepth == 0;
    }

    /**
     * Checks if book has valid inside market (best bid and ask exist).
     *
     * @return true if both best bid and ask are present
     */
    public boolean hasInsideMarket() {
        return bidDepth > 0 && askDepth > 0;
    }

    // ==================== LEVEL UPDATES ====================

    /**
     * Updates or inserts bid level.
     *
     * @param price the price level
     * @param quantity the quantity (0 or negative to delete)
     * @param orderCount number of orders at this level
     * @complexity O(log n) + O(n) for insertion/deletion
     */
    public void updateBid(double price, double quantity, int orderCount) {
        if (config.isValidateLevels() && price <= 0.0) {
            throw new IllegalArgumentException("Price must be positive, got: " + price);
        }
        if (config.isValidateLevels() && quantity < 0.0) {
            throw new IllegalArgumentException("Quantity cannot be negative, got: " + quantity);
        }
        if (config.isValidateLevels() && orderCount < 0) {
            throw new IllegalArgumentException("Order count cannot be negative, got: " + orderCount);
        }

        // Binary search to find insertion point
        int index = binarySearchBids(price);

        if (index >= 0) {
            // Price exists - update or delete
            if (quantity <= 0.0 && config.isTrimEmptyLevels()) {
                deleteBidLevel(index);
            } else {
                bidLevels[index] = new PriceLevel(price, quantity, orderCount);
            }
        } else {
            // Price doesn't exist - insert if quantity > 0
            if (quantity > 0.0) {
                int insertionPoint = -(index + 1);
                insertBidLevel(insertionPoint, price, quantity, orderCount);
            }
        }
    }

    /**
     * Updates or inserts ask level.
     *
     * @param price the price level
     * @param quantity the quantity (0 or negative to delete)
     * @param orderCount number of orders at this level
     * @complexity O(log n) + O(n) for insertion/deletion
     */
    public void updateAsk(double price, double quantity, int orderCount) {
        if (config.isValidateLevels() && price <= 0.0) {
            throw new IllegalArgumentException("Price must be positive, got: " + price);
        }
        if (config.isValidateLevels() && quantity < 0.0) {
            throw new IllegalArgumentException("Quantity cannot be negative, got: " + quantity);
        }
        if (config.isValidateLevels() && orderCount < 0) {
            throw new IllegalArgumentException("Order count cannot be negative, got: " + orderCount);
        }

        // Binary search to find insertion point
        int index = binarySearchAsks(price);

        if (index >= 0) {
            // Price exists - update or delete
            if (quantity <= 0.0 && config.isTrimEmptyLevels()) {
                deleteAskLevel(index);
            } else {
                askLevels[index] = new PriceLevel(price, quantity, orderCount);
            }
        } else {
            // Price doesn't exist - insert if quantity > 0
            if (quantity > 0.0) {
                int insertionPoint = -(index + 1);
                insertAskLevel(insertionPoint, price, quantity, orderCount);
            }
        }
    }

    /**
     * Deletes bid level at specified price.
     *
     * @param price the price level to delete
     * @return true if level was deleted, false if not found
     * @complexity O(log n) + O(n)
     */
    public boolean deleteBid(double price) {
        int index = binarySearchBids(price);
        if (index >= 0) {
            deleteBidLevel(index);
            return true;
        }
        return false;
    }

    /**
     * Deletes ask level at specified price.
     *
     * @param price the price level to delete
     * @return true if level was deleted, false if not found
     * @complexity O(log n) + O(n)
     */
    public boolean deleteAsk(double price) {
        int index = binarySearchAsks(price);
        if (index >= 0) {
            deleteAskLevel(index);
            return true;
        }
        return false;
    }

    /**
     * Clears all levels from the book.
     *
     * @complexity O(1)
     */
    public void clear() {
        Arrays.fill(bidLevels, 0, bidDepth, null);
        Arrays.fill(askLevels, 0, askDepth, null);
        bidDepth = 0;
        askDepth = 0;
    }

    /**
     * Sets a new ID for this book.
     *
     * @param id the new ID
     */
    public void setId(long id) {
        this.id = id;
    }

    // ==================== DERIVED METRICS ====================

    /**
     * Calculates bid-ask spread (ask - bid).
     *
     * @return spread in price units, or NaN if no inside market
     * @complexity O(1)
     */
    public double getSpread() {
        if (!hasInsideMarket()) {
            return Double.NaN;
        }
        return getBestAsk().getPrice() - getBestBid().getPrice();
    }

    /**
     * Calculates mid price (average of best bid and ask).
     *
     * @return mid price, or NaN if no inside market
     * @complexity O(1)
     */
    public double getMidPrice() {
        if (!hasInsideMarket()) {
            return Double.NaN;
        }
        return (getBestBid().getPrice() + getBestAsk().getPrice()) / 2.0;
    }

    /**
     * Calculates book imbalance (bid volume / total volume).
     *
     * @return value from 0.0 (all asks) to 1.0 (all bids), or 0.5 if both sides equal
     * @complexity O(n) where n = actual depth
     */
    public double getBookImbalance() {
        if (isEmpty()) {
            return 0.5; // neutral
        }
        double bidVol = getTotalBidVolume();
        double askVol = getTotalAskVolume();
        double total = bidVol + askVol;
        return total > 0.0 ? bidVol / total : 0.5;
    }

    /**
     * Calculates total bid volume across all levels.
     *
     * @return sum of all bid quantities
     * @complexity O(n)
     */
    public double getTotalBidVolume() {
        return getTotalBidVolume(bidDepth);
    }

    /**
     * Calculates total ask volume across all levels.
     *
     * @return sum of all ask quantities
     * @complexity O(n)
     */
    public double getTotalAskVolume() {
        return getTotalAskVolume(askDepth);
    }

    /**
     * Calculates total bid volume for top N levels.
     *
     * @param levels number of levels to include
     * @return sum of quantities
     * @complexity O(min(n, levels))
     */
    public double getTotalBidVolume(int levels) {
        double total = 0.0;
        int limit = Math.min(levels, bidDepth);
        for (int i = 0; i < limit; i++) {
            total += bidLevels[i].getQuantity();
        }
        return total;
    }

    /**
     * Calculates total ask volume for top N levels.
     *
     * @param levels number of levels to include
     * @return sum of quantities
     * @complexity O(min(n, levels))
     */
    public double getTotalAskVolume(int levels) {
        double total = 0.0;
        int limit = Math.min(levels, askDepth);
        for (int i = 0; i < limit; i++) {
            total += askLevels[i].getQuantity();
        }
        return total;
    }

    /**
     * Calculates the average price to fill a given quantity on the bid side.
     *
     * <p>This is the volume-weighted average price (VWAP) across multiple levels
     * needed to fill the requested quantity. Useful for market making to estimate
     * the effective price for hitting bids.</p>
     *
     * @param quantity the quantity to fill (must be positive)
     * @return volume-weighted average price, or NaN if insufficient liquidity or invalid input
     * @complexity O(n) worst case, typically O(1) to O(5) for normal quantities
     */
    public double getAverageBidPriceForQuantity(double quantity) {
        if (quantity <= 0.0 || bidDepth == 0) {
            return Double.NaN;
        }

        double remainingQty = quantity;
        double weightedPriceSum = 0.0;
        double filledQty = 0.0;

        for (int i = 0; i < bidDepth && remainingQty > 0.0; i++) {
            PriceLevel level = bidLevels[i];
            double levelQty = level.getQuantity();
            double fillQty = Math.min(remainingQty, levelQty);

            weightedPriceSum += level.getPrice() * fillQty;
            filledQty += fillQty;
            remainingQty -= fillQty;
        }

        if (filledQty == 0.0) {
            return Double.NaN;  // No liquidity
        }

        return weightedPriceSum / filledQty;
    }

    /**
     * Calculates the average price to fill a given quantity on the ask side.
     *
     * <p>This is the volume-weighted average price (VWAP) across multiple levels
     * needed to fill the requested quantity. Useful for market making to estimate
     * the effective price for lifting asks.</p>
     *
     * @param quantity the quantity to fill (must be positive)
     * @return volume-weighted average price, or NaN if insufficient liquidity or invalid input
     * @complexity O(n) worst case, typically O(1) to O(5) for normal quantities
     */
    public double getAverageAskPriceForQuantity(double quantity) {
        if (quantity <= 0.0 || askDepth == 0) {
            return Double.NaN;
        }

        double remainingQty = quantity;
        double weightedPriceSum = 0.0;
        double filledQty = 0.0;

        for (int i = 0; i < askDepth && remainingQty > 0.0; i++) {
            PriceLevel level = askLevels[i];
            double levelQty = level.getQuantity();
            double fillQty = Math.min(remainingQty, levelQty);

            weightedPriceSum += level.getPrice() * fillQty;
            filledQty += fillQty;
            remainingQty -= fillQty;
        }

        if (filledQty == 0.0) {
            return Double.NaN;  // No liquidity
        }

        return weightedPriceSum / filledQty;
    }

    /**
     * Calculates the worst (final) price to fill a given quantity on the bid side.
     *
     * <p>Returns the price of the last level that would be touched to fill the
     * requested quantity. This represents the worst execution price and is useful
     * for slippage analysis and order sizing in market making.</p>
     *
     * @param quantity the quantity to fill (must be positive)
     * @return price of the final level touched, or NaN if insufficient liquidity or invalid input
     * @complexity O(n) worst case, typically O(1) to O(5) for normal quantities
     */
    public double getWorstBidPriceForQuantity(double quantity) {
        if (quantity <= 0.0 || bidDepth == 0) {
            return Double.NaN;
        }

        double remainingQty = quantity;

        for (int i = 0; i < bidDepth; i++) {
            PriceLevel level = bidLevels[i];
            double levelQty = level.getQuantity();

            if (remainingQty <= levelQty) {
                // This level can fill the remaining quantity
                return level.getPrice();
            }

            remainingQty -= levelQty;
        }

        // Insufficient liquidity - return worst available price
        return bidDepth > 0 ? bidLevels[bidDepth - 1].getPrice() : Double.NaN;
    }

    /**
     * Calculates the worst (final) price to fill a given quantity on the ask side.
     *
     * <p>Returns the price of the last level that would be touched to fill the
     * requested quantity. This represents the worst execution price and is useful
     * for slippage analysis and order sizing in market making.</p>
     *
     * @param quantity the quantity to fill (must be positive)
     * @return price of the final level touched, or NaN if insufficient liquidity or invalid input
     * @complexity O(n) worst case, typically O(1) to O(5) for normal quantities
     */
    public double getWorstAskPriceForQuantity(double quantity) {
        if (quantity <= 0.0 || askDepth == 0) {
            return Double.NaN;
        }

        double remainingQty = quantity;

        for (int i = 0; i < askDepth; i++) {
            PriceLevel level = askLevels[i];
            double levelQty = level.getQuantity();

            if (remainingQty <= levelQty) {
                // This level can fill the remaining quantity
                return level.getPrice();
            }

            remainingQty -= levelQty;
        }

        // Insufficient liquidity - return worst available price
        return askDepth > 0 ? askLevels[askDepth - 1].getPrice() : Double.NaN;
    }

    // ==================== PRIVATE HELPER METHODS ====================

    /**
     * Binary search in bid levels (descending order, higher prices first).
     *
     * @param price the price to search for
     * @return index if found, -(insertion_point + 1) if not found
     */
    private int binarySearchBids(double price) {
        int low = 0;
        int high = bidDepth - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            double midPrice = bidLevels[mid].getPrice();

            if (midPrice > price) {
                low = mid + 1;  // descending order
            } else if (midPrice < price) {
                high = mid - 1;
            } else {
                return mid; // found
            }
        }
        return -(low + 1); // not found, return insertion point
    }

    /**
     * Binary search in ask levels (ascending order, lower prices first).
     *
     * @param price the price to search for
     * @return index if found, -(insertion_point + 1) if not found
     */
    private int binarySearchAsks(double price) {
        int low = 0;
        int high = askDepth - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            double midPrice = askLevels[mid].getPrice();

            if (midPrice < price) {
                low = mid + 1;  // ascending order
            } else if (midPrice > price) {
                high = mid - 1;
            } else {
                return mid; // found
            }
        }
        return -(low + 1); // not found, return insertion point
    }

    /**
     * Inserts a new bid level at the specified index.
     *
     * @param index the insertion index
     * @param price the price
     * @param quantity the quantity
     * @param orderCount the order count
     */
    private void insertBidLevel(int index, double price, double quantity, int orderCount) {
        if (bidDepth >= bidLevels.length) {
            // Book is full - drop lowest priority level (lowest bid price)
            bidDepth = bidLevels.length - 1;
        }

        // Shift elements right to make space
        if (bidDepth - index > 0) {
            System.arraycopy(bidLevels, index, bidLevels, index + 1, bidDepth - index);
        }
        bidLevels[index] = new PriceLevel(price, quantity, orderCount);
        bidDepth++;
    }

    /**
     * Inserts a new ask level at the specified index.
     *
     * @param index the insertion index
     * @param price the price
     * @param quantity the quantity
     * @param orderCount the order count
     */
    private void insertAskLevel(int index, double price, double quantity, int orderCount) {
        if (askDepth >= askLevels.length) {
            // Book is full - drop lowest priority level (highest ask price)
            askDepth = askLevels.length - 1;
        }

        // Shift elements right to make space
        if (askDepth - index > 0) {
            System.arraycopy(askLevels, index, askLevels, index + 1, askDepth - index);
        }
        askLevels[index] = new PriceLevel(price, quantity, orderCount);
        askDepth++;
    }

    /**
     * Deletes bid level at the specified index.
     *
     * @param index the index to delete
     */
    private void deleteBidLevel(int index) {
        // Shift elements left to fill gap
        if (bidDepth - index - 1 > 0) {
            System.arraycopy(bidLevels, index + 1, bidLevels, index, bidDepth - index - 1);
        }
        bidLevels[bidDepth - 1] = null; // clear reference
        bidDepth--;
    }

    /**
     * Deletes ask level at the specified index.
     *
     * @param index the index to delete
     */
    private void deleteAskLevel(int index) {
        // Shift elements left to fill gap
        if (askDepth - index - 1 > 0) {
            System.arraycopy(askLevels, index + 1, askLevels, index, askDepth - index - 1);
        }
        askLevels[askDepth - 1] = null; // clear reference
        askDepth--;
    }
}
