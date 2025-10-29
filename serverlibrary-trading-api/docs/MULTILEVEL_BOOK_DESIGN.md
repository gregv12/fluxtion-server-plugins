# Multilevel MarketDataBook Design Specification

**Project:** fluxtion-server-plugins
**Module:** serverlibrary-trading-api
**Date:** 2025-10-28
**Status:** Design Proposal

## Executive Summary

This document proposes the design and implementation of a multilevel order book structure to replace or augment the
current single-level `MarketDataBook` class. The design provides configurable depth, efficient operations, and multiple
implementation strategies optimized for different use cases.

**Key Recommendation:** Fixed-depth array-based implementation with PriceLevel objects (10 levels per side) provides the
optimal balance of performance, maintainability, and memory efficiency for most trading applications.

---

## Table of Contents

1. [Current State Analysis](#current-state-analysis)
2. [Requirements](#requirements)
3. [Design Considerations](#design-considerations)
4. [Data Structure Options](#data-structure-options)
5. [Recommended Architecture](#recommended-architecture)
6. [Implementation Plan](#implementation-plan)
7. [API Design](#api-design)
8. [Testing Strategy](#testing-strategy)
9. [Migration Path](#migration-path)
10. [Performance Considerations](#performance-considerations)
11. [Appendices](#appendices)

---

## 1. Current State Analysis

### 1.1 Existing MarketDataBook

**Location:**
`serverlibrary-trading-api/src/main/java/com/fluxtion/server/plugin/trading/service/marketdata/MarketDataBook.java`

**Current Structure:**

```java
public final class MarketDataBook implements MarketFeedEvent {
    private String feedName;
    private String venueName;
    private String symbol;
    private long id;

    // Top-of-book only
    private double bidPrice;
    private double bidQuantity;
    private int bidOrderCount;

    private double askPrice;
    private double askQuantity;
    private int askOrderCount;
}
```

**Characteristics:**

- ✅ Simple, lightweight structure
- ✅ Lombok-generated boilerplate
- ✅ Implements `MarketFeedEvent` for Fluxtion integration
- ✅ Immutable event object semantics
- ❌ **Limitation:** Only stores top-of-book (best bid/ask)
- ❌ **Limitation:** No price level depth information
- ❌ **Limitation:** Cannot represent full order book state

### 1.2 Current Usage

**Consumers:**

- `MarketDataBookNode` - subscribes and stores latest book snapshot
- `MarketDataBookGenerator` - generates random book data for testing
- Trading strategies requiring market data
- Market data feeds (QuickFIX/J integration, mock venues)

**Integration Points:**

- Fluxtion event processing graph
- Admin command registry (book snapshot queries)
- Market data feed subscriptions

---

## 2. Requirements

### 2.1 Functional Requirements

| ID    | Requirement                                             | Priority |
|-------|---------------------------------------------------------|----------|
| FR-1  | Store multiple price levels per side (bid/ask)          | MUST     |
| FR-2  | Support configurable book depth (5-50 levels)           | MUST     |
| FR-3  | Provide O(1) access to best bid/ask                     | MUST     |
| FR-4  | Support level updates (add/modify/delete)               | MUST     |
| FR-5  | Maintain sorted order (bids descending, asks ascending) | MUST     |
| FR-6  | Track quantity and order count per level                | MUST     |
| FR-7  | Support full book snapshots                             | SHOULD   |
| FR-8  | Support incremental updates                             | SHOULD   |
| FR-9  | Calculate derived metrics (spread, mid, imbalance)      | SHOULD   |
| FR-10 | Backward compatibility with existing MarketDataBook     | SHOULD   |

### 2.2 Non-Functional Requirements

| ID    | Requirement                | Priority | Target                   |
|-------|----------------------------|----------|--------------------------|
| NFR-1 | Low latency operations     | MUST     | < 1μs for best bid/ask   |
| NFR-2 | Minimal GC pressure        | MUST     | < 100 bytes/update       |
| NFR-3 | Memory efficiency          | SHOULD   | < 5KB per book           |
| NFR-4 | Thread-safe (if needed)    | COULD    | TBD based on usage       |
| NFR-5 | Cache-friendly data layout | SHOULD   | Sequential memory access |

### 2.3 Design Goals

1. **Performance:** Sub-microsecond access to best bid/ask
2. **Scalability:** Handle hundreds of symbols with deep books
3. **Maintainability:** Clean, understandable code structure
4. **Flexibility:** Support multiple implementation strategies
5. **Integration:** Seamless Fluxtion event processing integration

---

## 3. Design Considerations

### 3.1 Key Design Questions

#### Q1: Should we use a PriceLevel class?

**Answer: ✅ YES**

**Rationale:**

- **Abstraction:** Clean separation of concerns
- **Type Safety:** Compile-time enforcement of level structure
- **Extensibility:** Easy to add fields (timestamp, venue data, etc.)
- **Readability:** Self-documenting code

**Trade-offs:**
| Aspect | PriceLevel Class | Parallel Primitive Arrays |
|--------|------------------|---------------------------|
| Maintainability | ✅ Excellent | ❌ Poor |
| Performance | ⚠️ Good (with pooling) | ✅ Excellent |
| GC Pressure | ⚠️ Medium | ✅ Low |
| Extensibility | ✅ Easy | ❌ Difficult |

**Recommendation:** Use PriceLevel class with optional pooling if GC becomes an issue.

#### Q2: What internal data structure should we use?

**Answer: It depends on the use case** (see Section 4)

**Key Factors:**

1. **Expected book depth:** 5-10 levels vs 50+ levels
2. **Update frequency:** 100/sec vs 10,000/sec
3. **Access patterns:** Best bid/ask only vs full book analytics
4. **Latency requirements:** Millisecond vs microsecond
5. **GC sensitivity:** Trading (high) vs analytics (medium)

---

## 4. Data Structure Options

### 4.1 Comparison Matrix

| Structure           | Lookup   | Insert   | Delete   | Best Bid/Ask | Memory | GC Impact | Cache Locality |
|---------------------|----------|----------|----------|--------------|--------|-----------|----------------|
| **Sorted Array**    | O(log n) | O(n)     | O(n)     | **O(1)**     | ⭐⭐⭐    | ⭐⭐        | ⭐⭐⭐            |
| **TreeMap**         | O(log n) | O(log n) | O(log n) | O(log n)     | ⭐      | ⭐         | ⭐              |
| **Agrona HashMap**  | O(1)     | O(1)     | O(1)     | O(n)*        | ⭐⭐     | ⭐⭐⭐       | ⭐⭐             |
| **Parallel Arrays** | O(log n) | O(n)     | O(n)     | **O(1)**     | ⭐⭐⭐    | ⭐⭐⭐       | ⭐⭐⭐            |
| **LinkedList**      | O(n)     | O(1)**   | O(1)**   | **O(1)**     | ⭐⭐     | ⭐         | ⭐              |

\* Can be O(1) with separate index
\*\* Only at insertion point; finding insertion point is O(n)

⭐⭐⭐ = Excellent, ⭐⭐ = Good, ⭐ = Poor

### 4.2 Option A: Fixed-Depth Sorted Arrays (RECOMMENDED)

**Description:**

- Fixed-size arrays for bid/ask sides
- PriceLevel objects sorted by price
- Binary search for lookups
- Array shifting for inserts/deletes

**Structure:**

```java
private PriceLevel[] bidLevels;  // sorted descending by price
private PriceLevel[] askLevels;  // sorted ascending by price
private int bidDepth;  // actual number of levels (0 to array.length)
private int askDepth;  // actual number of levels
```

**Advantages:**

- ✅ **O(1) best bid/ask access** - Direct array index
- ✅ **Excellent cache locality** - Sequential memory layout
- ✅ **Predictable memory footprint** - Fixed allocation
- ✅ **Simple implementation** - Easy to understand and maintain
- ✅ **Low GC pressure** - Reuse array, update in place

**Disadvantages:**

- ❌ **O(n) inserts/deletes** - Array shifting required
- ❌ **Fixed maximum depth** - Cannot exceed array size
- ❌ **Wasted memory** - If typical depth << max depth

**Best For:**

- Books with 5-20 levels per side (covers 95%+ of use cases)
- Market data display and algorithmic trading
- When best bid/ask access is most frequent operation

**Recommended Configuration:**

- **Default depth:** 10 levels per side
- **Configurable:** Allow 5, 10, 20, 50 via constructor
- **Memory per book:** ~2KB (10 levels × 2 sides × ~100 bytes/level)

### 4.3 Option B: TreeMap-Based

**Description:**

- `TreeMap<Double, PriceLevel>` for each side
- Natural ordering for bids (reversed), asks (natural)

**Structure:**

```java
private TreeMap<Double, PriceLevel> bids;  // comparator: descending
private TreeMap<Double, PriceLevel> asks;  // comparator: ascending
```

**Advantages:**

- ✅ **O(log n) all operations** - Balanced performance
- ✅ **Unlimited depth** - Dynamic sizing
- ✅ **Efficient for sparse books** - No wasted memory

**Disadvantages:**

- ❌ **O(log n) best bid/ask** - Tree traversal required (firstEntry)
- ❌ **Higher GC pressure** - TreeNode allocations
- ❌ **Poor cache locality** - Scattered tree nodes
- ❌ **Higher memory overhead** - Node objects + references

**Best For:**

- Deep books (50+ levels)
- Full book reconstruction from snapshots
- Analytics and reporting
- When unlimited depth is required

### 4.4 Option C: Agrona-Based GC-Free (ULTRA-LOW LATENCY)

**Description:**

- Agrona `Object2ObjectHashMap` for price lookups
- Separate sorted index for ordered access
- Pooled PriceLevel objects (zero allocation)

**Structure:**

```java
private Object2ObjectHashMap<Double, PriceLevel> bidMap;
private Object2ObjectHashMap<Double, PriceLevel> askMap;
private double[] bidPriceIndex;  // sorted, for ordered iteration
private double[] askPriceIndex;  // sorted, for ordered iteration
private int bidIndexSize;
private int askIndexSize;
```

**Advantages:**

- ✅ **Zero GC pressure** - No allocations after initialization
- ✅ **O(1) price lookups** - HashMap access
- ✅ **Predictable latency** - No GC pauses
- ✅ **Already in project** - Agrona is a dependency

**Disadvantages:**

- ❌ **Complex implementation** - Dual data structure maintenance
- ❌ **More code** - Index synchronization required
- ❌ **Higher memory** - HashMap + index arrays
- ❌ **Overkill for most use cases**

**Best For:**

- High-frequency trading (HFT)
- Market making applications
- When sub-microsecond latency is critical
- GC-sensitive applications

### 4.5 Option D: Parallel Primitive Arrays (MAXIMUM PERFORMANCE)

**Description:**

- No PriceLevel class
- Parallel arrays for each field

**Structure:**

```java
// Bid side
private double[] bidPrices;
private double[] bidQuantities;
private int[] bidOrderCounts;
private int bidDepth;

// Ask side
private double[] askPrices;
private double[] askQuantities;
private int[] askOrderCounts;
private int askDepth;
```

**Advantages:**

- ✅ **Best cache locality** - All data in contiguous arrays
- ✅ **Minimal object overhead** - Only primitive arrays
- ✅ **Lowest GC pressure** - Primitives don't trigger GC
- ✅ **Best performance** - Direct memory access

**Disadvantages:**

- ❌ **Difficult to maintain** - Index synchronization across arrays
- ❌ **Error-prone** - Easy to desync arrays
- ❌ **Less flexible** - Hard to add fields
- ❌ **Poor readability** - No logical grouping

**Best For:**

- Extreme performance requirements
- When profiling shows Option A is insufficient
- Low-level system components

**Recommendation:** Start with Option A; migrate to Option D only if profiling proves necessary.

---

## 5. Recommended Architecture

### 5.1 Proposed Class Structure

```
serverlibrary-trading-api/src/main/java/com/fluxtion/server/plugin/trading/service/marketdata/
├── MarketDataBook.java              (existing - keep for backward compatibility)
├── PriceLevel.java                  (new - price level abstraction)
├── MultilevelMarketDataBook.java   (new - main implementation)
├── MultilevelBookConfig.java        (new - configuration)
└── MarketDataBookConverter.java     (new - conversion utilities)
```

### 5.2 Class Diagram

```
┌─────────────────────────────────┐
│    MarketFeedEvent             │
│      (interface)                │
└────────────┬────────────────────┘
             │ implements
             │
    ┌────────┴────────┐
    │                 │
    │                 │
┌───▼──────────┐  ┌──▼──────────────────────┐
│MarketDataBook│  │MultilevelMarketDataBook│
│(existing)     │  │(new)                    │
│              │  │                          │
│- bidPrice    │  │- bidLevels: PriceLevel[]│
│- askPrice    │  │- askLevels: PriceLevel[]│
│- bidQuantity │  │- bidDepth: int          │
│- askQuantity │  │- askDepth: int          │
└──────────────┘  │- config: Config         │
                  └───────┬──────────────────┘
                          │ contains
                          │
                    ┌─────▼──────┐
                    │ PriceLevel │
                    │            │
                    │- price     │
                    │- quantity  │
                    │- orderCount│
                    └────────────┘
```

### 5.3 Design Rationale

**Key Decisions:**

1. **Keep MarketDataBook:** Maintain backward compatibility for single-level use cases
2. **Create MultilevelMarketDataBook:** New class for multilevel support
3. **PriceLevel abstraction:** Clean API with potential for optimization
4. **Fixed-depth arrays:** Start simple, optimize later if needed
5. **Configuration object:** Allow customization of depth and behavior

---

## 6. Implementation Plan

### Phase 1: Core Classes (Week 1)

#### Task 1.1: Create PriceLevel Class

**File:** `PriceLevel.java`

```java
package com.fluxtion.server.plugin.trading.service.marketdata;

import lombok.Value;
import lombok.With;

/**
 * Represents a single price level in an order book.
 * Immutable value object for thread-safety and event semantics.
 */
@Value
@With
public class PriceLevel {
    double price;
    double quantity;
    int orderCount;

    /**
     * Creates an empty price level (zero quantity).
     */
    public static PriceLevel empty(double price) {
        return new PriceLevel(price, 0.0, 0);
    }

    /**
     * Checks if this level has no quantity.
     */
    public boolean isEmpty() {
        return quantity <= 0.0;
    }

    /**
     * Validates this price level.
     */
    public boolean isValid() {
        return price > 0.0 && quantity >= 0.0 && orderCount >= 0;
    }
}
```

**Test Coverage:**

- Construction and getters
- Empty level creation
- Validation logic
- Immutability (with methods)

#### Task 1.2: Create MultilevelBookConfig

**File:** `MultilevelBookConfig.java`

```java
package com.fluxtion.server.plugin.trading.service.marketdata;

import lombok.Builder;
import lombok.Value;

/**
 * Configuration for multilevel market data book behavior.
 */
@Value
@Builder
public class MultilevelBookConfig {

    /**
     * Maximum depth per side (bid/ask).
     * Default: 10 levels
     */
    @Builder.Default
    int maxDepth = 10;

    /**
     * Whether to trim empty levels automatically.
     * Default: true
     */
    @Builder.Default
    boolean trimEmptyLevels = true;

    /**
     * Whether to validate price levels on updates.
     * Default: true (disable for performance in production)
     */
    @Builder.Default
    boolean validateLevels = true;

    /**
     * Default configuration (10 levels, validation enabled).
     */
    public static MultilevelBookConfig defaultConfig() {
        return MultilevelBookConfig.builder().build();
    }

    /**
     * High-performance configuration (20 levels, validation disabled).
     */
    public static MultilevelBookConfig highPerformance() {
        return MultilevelBookConfig.builder()
                .maxDepth(20)
                .validateLevels(false)
                .build();
    }
}
```

#### Task 1.3: Create MultilevelMarketDataBook

**File:** `MultilevelMarketDataBook.java`

**Key Methods:**

- Constructors and builders
- Level access: `getBestBid()`, `getBestAsk()`, `getBidLevels()`, `getAskLevels()`
- Level updates: `updateBid()`, `updateAsk()`, `deleteLevel()`
- Derived metrics: `getSpread()`, `getMidPrice()`, `getBookImbalance()`
- Snapshots: `clear()`, `replaceBook()`
- Conversion: `toTopOfBook()`

See [Section 7: API Design](#7-api-design) for detailed method signatures.

### Phase 2: Operations & Algorithms (Week 1-2)

#### Task 2.1: Implement Core Operations

**Update Level Algorithm (Fixed-depth array):**

```java
/**
 * Updates or inserts a bid level at the specified price.
 *
 * @param price the price level to update
 * @param quantity the new quantity (0 to delete)
 * @param orderCount the number of orders at this level
 */
public void updateBid(double price, double quantity, int orderCount) {
    if (config.isValidateLevels() && price <= 0.0) {
        throw new IllegalArgumentException("Price must be positive");
    }

    // Binary search to find insertion point
    int index = binarySearchBids(price);

    if (index >= 0) {
        // Price exists - update in place
        if (quantity <= 0.0) {
            // Delete level by shifting array
            deleteBidLevel(index);
        } else {
            // Update existing level
            bidLevels[index] = new PriceLevel(price, quantity, orderCount);
        }
    } else {
        // Price doesn't exist - insert new level
        int insertionPoint = -(index + 1);
        if (quantity > 0.0) {
            insertBidLevel(insertionPoint, price, quantity, orderCount);
        }
    }
}

private int binarySearchBids(double price) {
    // Binary search in descending order (higher prices first)
    int low = 0;
    int high = bidDepth - 1;

    while (low <= high) {
        int mid = (low + high) >>> 1;
        double midPrice = bidLevels[mid].getPrice();

        if (midPrice > price) {
            low = mid + 1;
        } else if (midPrice < price) {
            high = mid - 1;
        } else {
            return mid; // found
        }
    }
    return -(low + 1); // not found, return insertion point
}

private void insertBidLevel(int index, double price, double quantity, int orderCount) {
    if (bidDepth >= bidLevels.length) {
        // Book is full - drop lowest priority level (lowest bid)
        bidDepth = bidLevels.length - 1;
    }

    // Shift elements right to make space
    System.arraycopy(bidLevels, index, bidLevels, index + 1, bidDepth - index);
    bidLevels[index] = new PriceLevel(price, quantity, orderCount);
    bidDepth++;
}

private void deleteBidLevel(int index) {
    // Shift elements left to fill gap
    System.arraycopy(bidLevels, index + 1, bidLevels, index, bidDepth - index - 1);
    bidLevels[bidDepth - 1] = null; // clear reference
    bidDepth--;
}
```

**Similar algorithms for ask side (ascending order).**

#### Task 2.2: Implement Derived Metrics

```java
public double getSpread() {
    if (isEmpty()) {
        return Double.NaN;
    }
    return getBestAsk().getPrice() - getBestBid().getPrice();
}

public double getMidPrice() {
    if (isEmpty()) {
        return Double.NaN;
    }
    return (getBestBid().getPrice() + getBestAsk().getPrice()) / 2.0;
}

public double getBookImbalance() {
    if (isEmpty()) {
        return 0.0;
    }
    double bidVol = getTotalBidVolume();
    double askVol = getTotalAskVolume();
    return bidVol / (bidVol + askVol);
}

public double getTotalBidVolume(int levels) {
    double total = 0.0;
    int limit = Math.min(levels, bidDepth);
    for (int i = 0; i < limit; i++) {
        total += bidLevels[i].getQuantity();
    }
    return total;
}
```

### Phase 3: Integration (Week 2)

#### Task 3.1: Update MarketDataBookNode

Add support for multilevel books:

```java

@Getter
private MultilevelMarketDataBook multilevelBook;

public boolean onMarketData(MultilevelMarketDataBook book) {
    // Handle multilevel book updates
}
```

#### Task 3.2: Update MarketDataBookGenerator

Extend to generate multilevel test data:

```java
public static MultilevelMarketDataBook generateRandomMultilevel(
        MarketDataBookConfig config,
        int depth
) {
    // Generate random levels at progressively worse prices
}
```

#### Task 3.3: Create Conversion Utilities

**File:** `MarketDataBookConverter.java`

```java
public class MarketDataBookConverter {

    /**
     * Converts multilevel book to top-of-book only.
     */
    public static MarketDataBook toTopOfBook(MultilevelMarketDataBook multilevel) {
        if (multilevel.isEmpty()) {
            return new MarketDataBook(multilevel.getSymbol(), multilevel.getId());
        }

        PriceLevel bestBid = multilevel.getBestBid();
        PriceLevel bestAsk = multilevel.getBestAsk();

        return new MarketDataBook(
                multilevel.getFeedName(),
                multilevel.getVenueName(),
                multilevel.getSymbol(),
                multilevel.getId(),
                bestBid.getPrice(),
                bestBid.getQuantity(),
                bestAsk.getPrice(),
                bestAsk.getQuantity()
        );
    }

    /**
     * Converts top-of-book to single-level multilevel book.
     */
    public static MultilevelMarketDataBook fromTopOfBook(MarketDataBook book) {
        MultilevelMarketDataBook multilevel = new MultilevelMarketDataBook(
                book.getFeedName(),
                book.getVenueName(),
                book.getSymbol(),
                book.getId(),
                MultilevelBookConfig.defaultConfig()
        );

        multilevel.updateBid(book.getBidPrice(), book.getBidQuantity(), book.getBidOrderCount());
        multilevel.updateAsk(book.getAskPrice(), book.getAskQuantity(), book.getAskOrderCount());

        return multilevel;
    }
}
```

### Phase 4: Testing (Week 2-3)

See [Section 8: Testing Strategy](#8-testing-strategy)

### Phase 5: Documentation & Migration (Week 3)

- Update CLAUDE.md with new classes
- Create migration guide
- Update examples and demos
- Performance benchmarking

---

## 7. API Design

### 7.1 MultilevelMarketDataBook Core API

```java
package com.fluxtion.server.plugin.trading.service.marketdata;

import lombok.Getter;
import lombok.ToString;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Multilevel market data book supporting configurable depth per side.
 *
 * <p>Thread-safety: This class is NOT thread-safe. External synchronization
 * required if accessed from multiple threads.</p>
 *
 * <p>Performance: O(1) best bid/ask access, O(log n) updates for level insertion.</p>
 */
@ToString
public class MultilevelMarketDataBook implements MarketFeedEvent {

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

    private final PriceLevel[] bidLevels;
    private final PriceLevel[] askLevels;
    private int bidDepth;
    private int askDepth;

    // ==================== CONSTRUCTORS ====================

    /**
     * Creates empty book with default configuration (10 levels).
     */
    public MultilevelMarketDataBook(String feedName, String venueName,
                                    String symbol, long id) {
        this(feedName, venueName, symbol, id, MultilevelBookConfig.defaultConfig());
    }

    /**
     * Creates empty book with custom configuration.
     */
    public MultilevelMarketDataBook(String feedName, String venueName,
                                    String symbol, long id,
                                    MultilevelBookConfig config) {
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
     * @complexity O(min ( n, levels))
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
     * @complexity O(min ( n, levels))
     */
    public List<PriceLevel> getAskLevels(int levels) {
        int count = Math.min(levels, askDepth);
        return Collections.unmodifiableList(
                Arrays.asList(Arrays.copyOf(askLevels, count))
        );
    }

    /**
     * Gets current bid depth (number of levels).
     */
    public int getBidDepth() {
        return bidDepth;
    }

    /**
     * Gets current ask depth (number of levels).
     */
    public int getAskDepth() {
        return askDepth;
    }

    /**
     * Checks if book is empty (no bids or asks).
     */
    public boolean isEmpty() {
        return bidDepth == 0 && askDepth == 0;
    }

    /**
     * Checks if book has valid inside market (best bid and ask exist).
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
        // Implementation in Phase 2
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
        // Implementation in Phase 2
    }

    /**
     * Deletes bid level at specified price.
     *
     * @param price the price level to delete
     * @return true if level was deleted, false if not found
     * @complexity O(log n) + O(n)
     */
    public boolean deleteBid(double price) {
        // Implementation in Phase 2
    }

    /**
     * Deletes ask level at specified price.
     *
     * @param price the price level to delete
     * @return true if level was deleted, false if not found
     * @complexity O(log n) + O(n)
     */
    public boolean deleteAsk(double price) {
        // Implementation in Phase 2
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

    // ==================== DERIVED METRICS ====================

    /**
     * Calculates bid-ask spread (ask - bid).
     *
     * @return spread in price units, or NaN if no inside market
     * @complexity O(1)
     */
    public double getSpread() {
        // Implementation in Phase 2
    }

    /**
     * Calculates mid price (average of best bid and ask).
     *
     * @return mid price, or NaN if no inside market
     * @complexity O(1)
     */
    public double getMidPrice() {
        // Implementation in Phase 2
    }

    /**
     * Calculates book imbalance (bid volume / total volume).
     *
     * @return value from 0.0 (all asks) to 1.0 (all bids)
     * @complexity O(n) where n = actual depth
     */
    public double getBookImbalance() {
        // Implementation in Phase 2
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
     * @complexity O(min ( n, levels))
     */
    public double getTotalBidVolume(int levels) {
        // Implementation in Phase 2
    }

    /**
     * Calculates total ask volume for top N levels.
     *
     * @param levels number of levels to include
     * @return sum of quantities
     * @complexity O(min ( n, levels))
     */
    public double getTotalAskVolume(int levels) {
        // Implementation in Phase 2
    }

    // ==================== CONVERSION ====================

    /**
     * Converts to top-of-book only MarketDataBook.
     *
     * @return single-level book with best bid/ask
     */
    public MarketDataBook toTopOfBook() {
        // Use MarketDataBookConverter
    }
}
```

### 7.2 Usage Examples

#### Example 1: Create and Update Book

```java
// Create book with default config (10 levels)
MultilevelMarketDataBook book = new MultilevelMarketDataBook(
                "exchange1",
                "venue1",
                "BTCUSD",
                12345L
        );

// Add some levels
book.

updateBid(50000.0,1.5,3);  // price, quantity, order count
book.

updateBid(49999.0,2.0,5);
book.

updateBid(49998.0,1.0,2);

book.

updateAsk(50001.0,1.2,2);
book.

updateAsk(50002.0,1.8,4);

// Access best bid/ask
PriceLevel bestBid = book.getBestBid();
System.out.

println("Best bid: "+bestBid.getPrice() +" @ "+bestBid.

getQuantity());

// Calculate metrics
double spread = book.getSpread();  // 1.0
double mid = book.getMidPrice();   // 50000.5
double imbalance = book.getBookImbalance();  // > 0.5 (more bids)

// Get top 5 levels
List<PriceLevel> top5Bids = book.getBidLevels(5);
```

#### Example 2: Integration with MarketDataBookNode

```java
MarketDataBookNode node = new MarketDataBookNode("trader1");
node.

setSubscription("feed1","venue1","ETHUSD");

// Handle multilevel book update
@Override
public boolean onMarketData(MultilevelMarketDataBook book) {
    if (book.getSymbol().equals(subscribeSymbol)) {
        // Use book for trading logic
        if (book.getSpread() < 0.10) {
            // Tight spread - good for trading
        }

        // Check depth
        if (book.getBidDepth() >= 5 && book.getAskDepth() >= 5) {
            // Sufficient liquidity
        }

        return true;
    }
    return false;
}
```

#### Example 3: Converting Between Formats

```java
// Multilevel to top-of-book
MultilevelMarketDataBook multilevel = // ...
        MarketDataBook
topOfBook =MarketDataBookConverter.

toTopOfBook(multilevel);

// Top-of-book to multilevel
MarketDataBook simple = // ...
        MultilevelMarketDataBook
expanded =MarketDataBookConverter.

fromTopOfBook(simple);
```

---

## 8. Testing Strategy

### 8.1 Unit Tests

**File:** `MultilevelMarketDataBookTest.java`

**Test Categories:**

#### 8.1.1 Construction Tests

- `testEmptyBookCreation()`
- `testBookWithCustomConfig()`
- `testMetadataStorage()`

#### 8.1.2 Level Update Tests

- `testUpdateBidLevel_Insert()`
- `testUpdateBidLevel_Modify()`
- `testUpdateBidLevel_Delete()`
- `testUpdateBidLevel_ZeroQuantity()`
- `testUpdateAskLevel_Insert()`
- `testUpdateAskLevel_Modify()`
- `testUpdateAskLevel_Delete()`

#### 8.1.3 Sorting Tests

- `testBidsSortedDescending()`
- `testAsksSortedAscending()`
- `testInsertionMaintainsSorting()`
- `testDeletionMaintainsSorting()`

#### 8.1.4 Edge Cases

- `testFullBook_DropsLowestPriority()`
- `testEmptyBook_AllOperations()`
- `testSingleLevel_BothSides()`
- `testClear_RemovesAllLevels()`
- `testDuplicatePrice_Updates()`
- `testInvalidPrice_ThrowsException()`

#### 8.1.5 Derived Metrics Tests

- `testSpread_ValidInsideMarket()`
- `testSpread_EmptyBook_ReturnsNaN()`
- `testMidPrice_Calculation()`
- `testBookImbalance_Range()`
- `testTotalVolume_AllLevels()`
- `testTotalVolume_TopNLevels()`

#### 8.1.6 Conversion Tests

- `testToTopOfBook_PreservesMetadata()`
- `testToTopOfBook_EmptyBook()`
- `testFromTopOfBook_CreatesSingleLevel()`

### 8.2 Integration Tests

**File:** `MultilevelMarketDataBookIntegrationTest.java`

#### 8.2.1 MarketDataBookNode Integration

- `testNodeSubscription_MultilevelBook()`
- `testNodeFiltering_MultilevelBook()`
- `testAdminCommand_MultilevelBookSnapshot()`

#### 8.2.2 Generator Integration

- `testGenerateRandomMultilevelBook()`
- `testGeneratedBookDepth()`
- `testGeneratedBookSorting()`

#### 8.2.3 Event Flow Testing

- `testMultilevelBook_ThroughFluxtionGraph()`
- `testConversion_RoundTrip()`

### 8.3 Performance Tests

**File:** `MultilevelMarketDataBookBenchmark.java`

Use JMH (Java Microbenchmark Harness) for accurate benchmarking:

```java

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
@State(Scope.Thread)
public class MultilevelMarketDataBookBenchmark {

    private MultilevelMarketDataBook book;

    @Setup
    public void setup() {
        book = new MultilevelMarketDataBook(
                "feed", "venue", "SYM", 1L,
                MultilevelBookConfig.builder().maxDepth(10).build()
        );
        // Pre-populate with 10 levels
        for (int i = 0; i < 10; i++) {
            book.updateBid(100.0 - i, 1.0, 1);
            book.updateAsk(101.0 + i, 1.0, 1);
        }
    }

    @Benchmark
    public PriceLevel benchmarkGetBestBid() {
        return book.getBestBid();  // Target: < 10ns
    }

    @Benchmark
    public PriceLevel benchmarkGetBestAsk() {
        return book.getBestAsk();  // Target: < 10ns
    }

    @Benchmark
    public void benchmarkUpdateExistingLevel() {
        book.updateBid(95.0, 2.0, 2);  // Target: < 500ns
    }

    @Benchmark
    public void benchmarkInsertNewLevel() {
        book.updateBid(94.5, 1.5, 1);  // Target: < 1000ns
        book.deleteBid(94.5);  // cleanup
    }

    @Benchmark
    public double benchmarkCalculateSpread() {
        return book.getSpread();  // Target: < 50ns
    }

    @Benchmark
    public double benchmarkCalculateImbalance() {
        return book.getBookImbalance();  // Target: < 500ns
    }
}
```

**Performance Targets:**

| Operation             | Target Latency | Notes                  |
|-----------------------|----------------|------------------------|
| getBestBid/Ask()      | < 10ns         | Direct array access    |
| updateExistingLevel() | < 500ns        | Binary search + update |
| insertNewLevel()      | < 1μs          | Binary search + shift  |
| deleteLevel()         | < 1μs          | Binary search + shift  |
| getSpread()           | < 50ns         | Two array accesses     |
| getBookImbalance()    | < 500ns        | Iterate all levels     |
| getTotalVolume(10)    | < 500ns        | Iterate 10 levels      |

### 8.4 Test Data Generators

```java
public class MultilevelBookTestData {

    /**
     * Generates realistic book with tight spread and decreasing liquidity.
     */
    public static MultilevelMarketDataBook realisticBook(String symbol, int depth) {
        MultilevelMarketDataBook book = new MultilevelMarketDataBook(
                "test", "test", symbol, 1L,
                MultilevelBookConfig.builder().maxDepth(depth).build()
        );

        double basePrice = 100.0;
        double tickSize = 0.01;

        for (int i = 0; i < depth; i++) {
            // Bids: decreasing price, decreasing quantity
            book.updateBid(
                    basePrice - (i * tickSize),
                    10.0 / (i + 1),  // decreasing liquidity
                    (int) (5.0 / (i + 1) + 1)
            );

            // Asks: increasing price, decreasing quantity
            book.updateAsk(
                    basePrice + tickSize + (i * tickSize),
                    10.0 / (i + 1),
                    (int) (5.0 / (i + 1) + 1)
            );
        }

        return book;
    }

    /**
     * Generates crossed book (bid > ask) for error testing.
     */
    public static MultilevelMarketDataBook crossedBook() {
        // ...
    }

    /**
     * Generates sparse book with gaps in price levels.
     */
    public static MultilevelMarketDataBook sparseBook() {
        // ...
    }
}
```

---

## 9. Migration Path

### 9.1 Backward Compatibility Strategy

**Goal:** Zero breaking changes for existing code.

#### Strategy 1: Parallel Classes (RECOMMENDED)

- Keep `MarketDataBook` unchanged
- Add new `MultilevelMarketDataBook` alongside
- Consumers opt-in to multilevel support
- Conversion utilities bridge the gap

**Advantages:**

- ✅ No breaking changes
- ✅ Gradual migration
- ✅ Easy rollback

**Disadvantages:**

- ⚠️ Code duplication
- ⚠️ Two APIs to maintain

#### Strategy 2: Extend Existing Class

- Make `MarketDataBook` extend/implement multilevel support
- Add level arrays as optional fields
- Legacy accessors work as before

**Advantages:**

- ✅ Single API
- ✅ Smaller codebase

**Disadvantages:**

- ❌ Potential breaking changes
- ❌ Increased complexity
- ❌ Higher testing burden

**Recommendation:** Use Strategy 1 (parallel classes).

### 9.2 Migration Steps for Consumers

#### Step 1: No Changes Required

Existing code continues to work:

```java
// Existing code - unchanged
MarketDataBook book = // ...
double bestBid = book.getBidPrice();
```

#### Step 2: Opt-in to Multilevel

When ready, switch to multilevel:

```java
// New code - opt-in
MultilevelMarketDataBook book = // ...
        PriceLevel
bestBid =book.

getBestBid();

List<PriceLevel> top5 = book.getBidLevels(5);
```

#### Step 3: Use Conversion for Interop

Bridge old and new code:

```java
// Receive old format, convert to new
MarketDataBook legacy = // ...
        MultilevelMarketDataBook
multilevel =
        MarketDataBookConverter.

fromTopOfBook(legacy);

// Or convert back
MarketDataBook topOfBook = multilevel.toTopOfBook();
```

### 9.3 Deprecation Plan

**Timeline:**

| Phase   | Timeline      | Action                                        |
|---------|---------------|-----------------------------------------------|
| Phase 1 | Release 0.3.0 | Introduce MultilevelMarketDataBook            |
| Phase 2 | Release 0.3.x | Document migration, provide examples          |
| Phase 3 | Release 0.4.0 | Mark MarketDataBook as @Deprecated (optional) |
| Phase 4 | Release 0.5.0 | Remove MarketDataBook (optional, far future)  |

**Recommendation:** Keep both classes indefinitely unless maintenance burden becomes significant.

---

## 10. Performance Considerations

### 10.1 Memory Footprint

**Per-book overhead:**

```
MultilevelMarketDataBook (10 levels, fixed array):
- Metadata: 80 bytes (strings, longs, refs)
- bidLevels array: 40 bytes (10 × 4 bytes ref)
- askLevels array: 40 bytes
- PriceLevel objects: 480 bytes (20 × 24 bytes/object)
- Config: 32 bytes (shared)
-----------------------------------------
Total: ~672 bytes per book

Comparison:
- MarketDataBook (top-of-book): ~120 bytes
- Overhead: 5.6x for 10-level depth
```

**Scaling:**

- 1,000 symbols: ~650 KB
- 10,000 symbols: ~6.5 MB (acceptable)

### 10.2 GC Pressure

**Sources of allocation:**

1. **PriceLevel creation:** Every update creates new object (immutable)
    - **Mitigation:** Object pooling (reuse PriceLevel instances)
    - **Alternative:** Make PriceLevel mutable with `reset()` method

2. **Array copying:** `getBidLevels()` copies array
    - **Mitigation:** Return view/iterator instead of copy
    - **Alternative:** Cache result until next update

3. **List creation:** Wrapping arrays in unmodifiable lists
    - **Mitigation:** Lazy initialization, cache lists

**Optimization Roadmap:**

1. **Start simple:** Immutable PriceLevel, direct array copies
2. **Profile:** Measure GC impact in real workload
3. **Optimize if needed:** Add pooling, caching, mutable objects

### 10.3 CPU Performance

**Hot paths:**

1. **getBestBid/Ask():** Direct array access - already optimal
2. **updateLevel():** Binary search + array shift
    - **Current:** O(log n) + O(n)
    - **Acceptable for:** n < 20 levels
    - **Optimization:** Use TreeMap if profiling shows bottleneck

3. **Iteration:** For metrics like imbalance
    - **Current:** O(n) linear scan
    - **Acceptable:** Typically only 5-10 levels used
    - **Optimization:** Cache results, lazy compute

### 10.4 Optimization Checklist

Before optimizing, **measure first:**

```java
// Add metrics to track
private long updateCount = 0;
private long totalUpdateNanos = 0;

public void updateBid(double price, double quantity, int orderCount) {
    long start = System.nanoTime();
    try {
        // ... update logic ...
    } finally {
        totalUpdateNanos += System.nanoTime() - start;
        updateCount++;
    }
}

public double getAverageUpdateLatencyNanos() {
    return updateCount > 0 ? (double) totalUpdateNanos / updateCount : 0.0;
}
```

**Decision tree:**

1. Is average update latency > 1μs?
    - NO: Current implementation is fine
    - YES: Profile to find bottleneck

2. Is GC causing pauses > 10ms?
    - NO: Current implementation is fine
    - YES: Add object pooling or switch to mutable PriceLevel

3. Is memory footprint > 10MB for your use case?
    - NO: Current implementation is fine
    - YES: Reduce max depth or use sparse representation

---

## 11. Appendices

### Appendix A: Alternative Designs Considered

#### A.1 Red-Black Tree Implementation

**Rejected:** Higher complexity, worse cache locality, not needed for shallow books.

#### A.2 Skip List

**Rejected:** Randomized structure not suitable for deterministic trading systems.

#### A.3 B-Tree

**Rejected:** Overkill for in-memory structure, better for disk-based storage.

### Appendix B: Competitive Analysis

#### B.1 Market Data Systems

| System              | Structure     | Depth     | Performance |
|---------------------|---------------|-----------|-------------|
| Bloomberg           | TreeMap-based | Unlimited | O(log n)    |
| Reuters             | Fixed array   | 10 levels | O(1) best   |
| Coinbase Pro        | Fixed array   | 50 levels | O(1) best   |
| Interactive Brokers | Sparse array  | 20 levels | O(1) best   |

**Conclusion:** Fixed arrays with 10-20 levels are industry standard for market data display and algorithmic trading.

### Appendix C: References

1. **Agrona Project:** https://github.com/real-logic/agrona
2. **Market Data Best Practices:** https://www.fixtrading.org/standards/
3. **Order Book Design Patterns:** Martin Fowler's enterprise patterns
4. **JMH Benchmarking:** https://openjdk.org/projects/code-tools/jmh/

### Appendix D: Glossary

| Term               | Definition                                    |
|--------------------|-----------------------------------------------|
| **Price Level**    | A single price point with aggregated quantity |
| **Top-of-book**    | Best bid and best ask only                    |
| **Inside market**  | Best bid and best ask (same as top-of-book)   |
| **Book depth**     | Number of price levels on one side            |
| **Book imbalance** | Ratio of bid volume to total volume           |
| **Spread**         | Difference between best ask and best bid      |
| **Mid price**      | Average of best bid and best ask              |
| **Tick size**      | Minimum price increment                       |

### Appendix E: Future Enhancements

Potential features for future releases:

1. **Timestamp tracking:** Add timestamp to each level
2. **Order details:** Track individual orders, not just aggregates
3. **Market-by-order:** Full order-level depth
4. **Venue fragmentation:** Combine books from multiple venues
5. **Historical snapshots:** Ring buffer of book states
6. **Delta updates:** Publish only changed levels
7. **Compression:** Compact representation for network transmission
8. **Persistence:** Serialize/deserialize book state

---

## Approval & Next Steps

### Approval Checklist

Before implementation, confirm:

- [ ] Design approach approved (fixed-depth arrays)
- [ ] API signatures reviewed and approved
- [ ] Performance targets agreed upon
- [ ] Testing strategy sufficient
- [ ] Migration plan acceptable
- [ ] Timeline reasonable (3 weeks)

### Implementation Team

- **Lead Developer:** TBD
- **Reviewers:** TBD
- **Testers:** TBD

### Success Criteria

1. All unit tests pass (> 95% coverage)
2. Performance benchmarks meet targets
3. Zero regressions in existing MarketDataBook tests
4. Documentation complete and reviewed
5. Example code provided and working

---

**Document Version:** 1.0
**Last Updated:** 2025-10-28
**Next Review:** After Phase 1 completion