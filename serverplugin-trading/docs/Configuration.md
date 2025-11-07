# Configuration Guide

Comprehensive guide to configuring the Mock Market Data Feed.

## Table of Contents

- [Configuration Overview](#configuration-overview)
- [MarketDataBookConfig](#marketdatabookconfig)
- [Configuration Methods](#configuration-methods)
- [Single-Level Configuration](#single-level-configuration)
- [Multi-Level Configuration](#multi-level-configuration)
- [Advanced Configuration](#advanced-configuration)
- [Configuration Patterns](#configuration-patterns)
- [Validation and Constraints](#validation-and-constraints)

## Configuration Overview

The Mock Market Data Feed uses `MarketDataBookConfig` objects to define market data behavior for each symbol. Configurations can be set:

1. **Programmatically** at startup
2. **Via admin commands** at runtime
3. **From JSON** for complex scenarios

### Configuration Lifecycle

```
┌─────────────┐
│   Create    │  New MarketDataBookConfig object
└──────┬──────┘
       │
       v
┌─────────────┐
│  Configure  │  Set properties (symbol, prices, volumes, etc.)
└──────┬──────┘
       │
       v
┌─────────────┐
│   Register  │  Add to marketDataBookConfigs list
└──────┬──────┘
       │
       v
┌─────────────┐
│   Publish   │  Feed generates and publishes data
└─────────────┘
```

## MarketDataBookConfig

### Complete Property Reference

#### Essential Properties

| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| `symbol` | String | ✅ | - | Trading symbol (e.g., "EURUSD") |
| `minPrice` | double | ✅ | - | Minimum price for random generation |
| `maxPrice` | double | ✅ | - | Maximum price for random generation |
| `minSpread` | double | ✅ | - | Minimum bid-ask spread |
| `maxSpread` | double | ✅ | - | Maximum bid-ask spread |
| `minVolume` | double | ✅ | - | Minimum volume per side |
| `maxVolume` | double | ✅ | - | Maximum volume per side |

#### Optional Properties

| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| `feedName` | String | ❌ | Auto | Name of the market data feed |
| `venueName` | String | ❌ | Auto | Name of the trading venue |
| `publishProbability` | double | ❌ | 1.0 | Probability of publishing (0.0-1.0) |
| `precisionDpsPrice` | int | ❌ | 3 | Decimal places for price rounding |
| `precisionDpsVolume` | int | ❌ | 3 | Decimal places for volume rounding |

#### Multi-Level Properties

| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| `multilevel` | boolean | ❌ | false | Enable multi-level order book |
| `multilevelDepth` | int | ❌ | 20 | Number of price levels per side |
| `multilevelBookConfig` | MultilevelBookConfig | ❌ | default | Advanced book configuration |

### Property Relationships

```
For Single-Level Books:
  spread = askPrice - bidPrice
  where: minSpread ≤ spread ≤ maxSpread
  and:   minPrice ≤ bidPrice ≤ maxPrice
  and:   bidPrice + minSpread ≤ askPrice ≤ bidPrice + maxSpread

For Multi-Level Books:
  Same as single-level, but:
  - Multiple levels generated per side
  - Decreasing liquidity away from best price
  - Price increment = tickSize (configurable)
```

## Configuration Methods

### Method 1: Programmatic (Java)

**Basic Example:**
```java
MarketDataBookConfig config = new MarketDataBookConfig();
config.setSymbol("EURUSD");
config.setMinPrice(1.0800);
config.setMaxPrice(1.0900);
config.setMinSpread(0.0001);
config.setMaxSpread(0.0005);
config.setMinVolume(100000);
config.setMaxVolume(1000000);

mockFeed.setMarketDataBookConfigs(List.of(config));
```

**With Optional Properties:**
```java
MarketDataBookConfig config = new MarketDataBookConfig();
config.setSymbol("GBPUSD");
config.setMinPrice(1.2400);
config.setMaxPrice(1.2600);
config.setMinSpread(0.0002);
config.setMaxSpread(0.0008);
config.setMinVolume(50000);
config.setMaxVolume(500000);
config.setPublishProbability(0.9); // 90% publish rate
config.setPrecisionDpsPrice(5);    // 5 decimal places

mockFeed.setMarketDataBookConfigs(List.of(config));
```

### Method 2: Admin Command

**Basic Command:**
```bash
mockMarketDataFeed.addConfig EURUSD 1.0800 1.0900 0.0001 0.0005 100000 1000000
```

**Multiple Symbols:**
```bash
mockMarketDataFeed.addConfig EURUSD 1.0800 1.0900 0.0001 0.0005 100000 1000000
mockMarketDataFeed.addConfig GBPUSD 1.2400 1.2600 0.0002 0.0008 50000 500000
mockMarketDataFeed.addConfig USDJPY 148.0 151.0 0.01 0.05 10000 100000
```

### Method 3: JSON

**Complete JSON Example:**
```json
{
  "symbol": "EURUSD",
  "minPrice": 1.0800,
  "maxPrice": 1.0900,
  "minSpread": 0.0001,
  "maxSpread": 0.0005,
  "minVolume": 100000,
  "maxVolume": 1000000,
  "feedName": "mockMarketDataFeed",
  "venueName": "mockVenue",
  "publishProbability": 0.9,
  "precisionDpsPrice": 5,
  "precisionDpsVolume": 2,
  "multilevel": false
}
```

**Command:**
```bash
mockMarketDataFeed.configureFromJson {"symbol":"EURUSD","minPrice":1.0800,"maxPrice":1.0900,"minSpread":0.0001,"maxSpread":0.0005,"minVolume":100000,"maxVolume":1000000}
```

## Single-Level Configuration

### Basic Single-Level

**Purpose:** Simple bid/ask quotes without order book depth.

**Configuration:**
```java
MarketDataBookConfig config = new MarketDataBookConfig();
config.setSymbol("EURUSD");
config.setMinPrice(1.0800);
config.setMaxPrice(1.0900);
config.setMinSpread(0.0001);
config.setMaxSpread(0.0005);
config.setMinVolume(100000);
config.setMaxVolume(1000000);
config.setMultilevel(false); // Single-level
```

**Generated Output:**
```
MarketDataBook(
  symbol=EURUSD,
  bid=1.08523,  # Random between 1.0800 and 1.0900
  bidVolume=654321,  # Random between 100000 and 1000000
  ask=1.08556,  # bid + random spread (0.0001 to 0.0005)
  askVolume=432100   # Random between 100000 and 1000000
)
```

### Fixed Single-Level

**Purpose:** Deterministic pricing for testing.

**Configuration:**
```java
// Via admin command
mockMarketDataFeed.setFixedSingleLevel EURUSD 1.0850 250000 1.0853 250000
```

**Equivalent Programmatic:**
```java
MarketDataBookConfig config = new MarketDataBookConfig();
config.setSymbol("EURUSD");
config.setMinPrice(1.0850);  // Same as max
config.setMaxPrice(1.0850);
config.setMinSpread(0.0003); // askPrice - bidPrice
config.setMaxSpread(0.0003); // Same as min
config.setMinVolume(250000); // Same as max
config.setMaxVolume(250000);
config.setPublishProbability(1.0);
config.setMultilevel(false);
```

**Generated Output (always):**
```
MarketDataBook(
  symbol=EURUSD,
  bid=1.0850,
  bidVolume=250000,
  ask=1.0853,
  askVolume=250000
)
```

### Precision Control

**Configuration:**
```java
config.setPrecisionDpsPrice(5);  // 5 decimal places
config.setPrecisionDpsVolume(2); // 2 decimal places
```

**Effect:**
```
Without precision: bid=1.085234567, volume=123456.789
With precision:    bid=1.08523,     volume=123456.79
```

## Multi-Level Configuration

### Basic Multi-Level

**Purpose:** Full order book with multiple price levels.

**Configuration:**
```java
MarketDataBookConfig config = new MarketDataBookConfig();
config.setSymbol("EURUSD");
config.setMinPrice(1.0800);
config.setMaxPrice(1.0900);
config.setMinSpread(0.0001);
config.setMaxSpread(0.0005);
config.setMinVolume(100000);
config.setMaxVolume(1000000);
config.setMultilevel(true);        // Enable multi-level
config.setMultilevelDepth(10);     // 10 levels per side

// Optional: Custom book config
MultilevelBookConfig bookConfig = MultilevelBookConfig.builder()
    .maxDepth(20)
    .build();
config.setMultilevelBookConfig(bookConfig);
```

**Generated Output:**
```
MultilevelMarketDataBook(
  symbol=EURUSD,
  bidLevels=[
    PriceLevel(price=1.08500, quantity=500000, orders=5),
    PriceLevel(price=1.08499, quantity=500000, orders=10),
    PriceLevel(price=1.08498, quantity=333000, orders=7),
    ...
  ],
  askLevels=[
    PriceLevel(price=1.08503, quantity=450000, orders=8),
    PriceLevel(price=1.08504, quantity=500000, orders=12),
    PriceLevel(price=1.08505, quantity=333000, orders=10),
    ...
  ]
)
```

### Fixed Multi-Level

**Purpose:** Deterministic multi-level book for testing.

**Configuration:**
```bash
# Via admin command
mockMarketDataFeed.setFixedMultiLevel EURUSD 1.0850 0.0003 500000 10 0.0001 10000000

# Parameters:
# - Mid price: 1.0850
# - Spread: 0.0003
# - Base volume: 500000
# - Depth: 10 levels
# - Tick size: 0.0001
# - Max quantity: 10000000 (reserved)
```

**Resulting Book:**
```
Level  Bid Price  Bid Qty    Ask Price  Ask Qty
----------------------------------------------------
  0    1.08485    500000     1.08515    500000
  1    1.08475    500000     1.08525    500000
  2    1.08465    333000     1.08535    333000
  3    1.08455    250000     1.08545    250000
  ...
```

### Liquidity Distribution

Multi-level books use realistic liquidity tapering:

```
Level 0 (best):  liquidityFactor = 1.0   (100% of base)
Level 1:         liquidityFactor = 0.5   (50% of base)
Level 2:         liquidityFactor = 0.33  (33% of base)
Level 3:         liquidityFactor = 0.25  (25% of base)
...
```

**Example:**
```java
config.setMinVolume(500000);
config.setMaxVolume(500000);
config.setMultilevelDepth(4);

// Generated volumes (approximate):
// Level 0: 500000
// Level 1: 250000
// Level 2: 166667
// Level 3: 125000
```

## Advanced Configuration

### Publish Probability

Controls how frequently quotes are published.

**Configuration:**
```java
config.setPublishProbability(0.8); // Publish 80% of scheduled ticks
```

**Effect:**
```
With probability 1.0:  Updates every tick
With probability 0.8:  Updates 80% of ticks (random gaps)
With probability 0.5:  Updates 50% of ticks (frequent gaps)
```

**Use Cases:**
- Simulate market gaps
- Test handling of stale data
- Reduce load in high-frequency scenarios

### Price Precision

Controls decimal place rounding for prices.

**Configuration:**
```java
// FX major pairs (5 decimals)
config.setPrecisionDpsPrice(5);  // 1.08523

// FX JPY pairs (3 decimals)
config.setPrecisionDpsPrice(3);  // 150.234

// Crypto (2 decimals)
config.setPrecisionDpsPrice(2);  // 50123.45
```

### Volume Precision

Controls decimal place rounding for volumes.

**Configuration:**
```java
// Whole units
config.setPrecisionDpsVolume(0);  // 100000

// Fractional (crypto)
config.setPrecisionDpsVolume(4);  // 0.1234
```

### MultilevelBookConfig

Advanced configuration for multi-level books.

**Configuration:**
```java
MultilevelBookConfig bookConfig = MultilevelBookConfig.builder()
    .maxDepth(50)  // Support up to 50 levels
    .build();

config.setMultilevelBookConfig(bookConfig);
```

**Properties:**
- `maxDepth`: Maximum number of levels the book can hold

## Configuration Patterns

### Pattern 1: Major FX Pairs

```java
MarketDataBookConfig eurusd = createFXMajorConfig("EURUSD", 1.08, 1.09);
MarketDataBookConfig gbpusd = createFXMajorConfig("GBPUSD", 1.24, 1.26);
MarketDataBookConfig usdjpy = createFXMajorConfig("USDJPY", 148.0, 151.0);

private MarketDataBookConfig createFXMajorConfig(String symbol, double min, double max) {
    MarketDataBookConfig config = new MarketDataBookConfig();
    config.setSymbol(symbol);
    config.setMinPrice(min);
    config.setMaxPrice(max);
    config.setMinSpread(symbol.endsWith("JPY") ? 0.01 : 0.0001);
    config.setMaxSpread(symbol.endsWith("JPY") ? 0.05 : 0.0005);
    config.setMinVolume(symbol.endsWith("JPY") ? 10000 : 100000);
    config.setMaxVolume(symbol.endsWith("JPY") ? 100000 : 1000000);
    config.setPrecisionDpsPrice(symbol.endsWith("JPY") ? 3 : 5);
    config.setPublishProbability(0.9);
    return config;
}
```

### Pattern 2: Crypto Pairs

```java
MarketDataBookConfig createCryptoConfig(String symbol, double min, double max) {
    MarketDataBookConfig config = new MarketDataBookConfig();
    config.setSymbol(symbol);
    config.setMinPrice(min);
    config.setMaxPrice(max);
    config.setMinSpread(min * 0.001);  // 0.1% spread
    config.setMaxSpread(min * 0.005);  // 0.5% spread
    config.setMinVolume(0.1);
    config.setMaxVolume(10.0);
    config.setPrecisionDpsPrice(2);
    config.setPrecisionDpsVolume(4);
    config.setPublishProbability(0.95);
    config.setMultilevel(true);  // Crypto typically has deep books
    config.setMultilevelDepth(20);
    return config;
}
```

### Pattern 3: Testing Portfolio

```java
List<MarketDataBookConfig> createTestingPortfolio() {
    List<MarketDataBookConfig> configs = new ArrayList<>();

    // Fixed price for unit tests
    configs.add(createFixedConfig("TEST_FIXED", 100.0, 100.5, 1000));

    // Random with tight constraints for integration tests
    configs.add(createTightRandomConfig("TEST_RANDOM", 100.0, 100.1));

    // Multi-level for depth testing
    configs.add(createMultilevelConfig("TEST_DEPTH", 100.0, 10));

    return configs;
}
```

### Pattern 4: Load Testing

```java
List<MarketDataBookConfig> createLoadTestConfigs(int symbolCount) {
    List<MarketDataBookConfig> configs = new ArrayList<>();

    for (int i = 0; i < symbolCount; i++) {
        MarketDataBookConfig config = new MarketDataBookConfig();
        config.setSymbol("LOAD_TEST_" + i);
        config.setMinPrice(100.0);
        config.setMaxPrice(110.0);
        config.setMinSpread(0.01);
        config.setMaxSpread(0.05);
        config.setMinVolume(1000);
        config.setMaxVolume(10000);
        config.setPublishProbability(1.0);  // Always publish
        configs.add(config);
    }

    return configs;
}
```

## Validation and Constraints

### Automatic Validations

The configuration system enforces these rules:

1. **Symbol Required:**
   - Must not be null or empty

2. **Price Ranges:**
   - `minPrice` ≤ `maxPrice`
   - Both must be positive

3. **Spread Ranges:**
   - `minSpread` ≤ `maxSpread`
   - Both must be non-negative
   - Practical: `maxSpread` < `(maxPrice - minPrice)`

4. **Volume Ranges:**
   - `minVolume` ≤ `maxVolume`
   - Both must be positive

5. **Publish Probability:**
   - 0.0 ≤ `publishProbability` ≤ 1.0

6. **Multi-Level Depth:**
   - `multilevelDepth` ≤ `multilevelBookConfig.maxDepth`
   - Must be positive

### Best Practice Constraints

#### Spread vs Price Range

```java
// Good: Spread range is small relative to price range
config.setMinPrice(1.08);
config.setMaxPrice(1.09);
config.setMinSpread(0.0001);  // 0.01% of price
config.setMaxSpread(0.0005);  // 0.05% of price

// Poor: Spread range is too large
config.setMinPrice(1.08);
config.setMaxPrice(1.09);
config.setMinSpread(0.01);    // 1% of price
config.setMaxSpread(0.05);    // 5% of price - unrealistic
```

#### Volume Ranges

```java
// Good: Reasonable volume variation
config.setMinVolume(100000);
config.setMaxVolume(1000000);  // 10x range

// Poor: Excessive variation
config.setMinVolume(100);
config.setMaxVolume(10000000); // 100000x range - unrealistic
```

#### Multi-Level Depth

```java
// Good: Practical depths
config.setMultilevelDepth(10);   // Common
config.setMultilevelDepth(20);   // Deep
config.setMultilevelDepth(50);   // Very deep

// Poor: Impractical
config.setMultilevelDepth(1);    // Use single-level instead
config.setMultilevelDepth(1000); // Excessive
```

### Configuration Checklist

Before deploying a configuration:

- [ ] Symbol is unique and meaningful
- [ ] Price range is realistic for instrument
- [ ] Spread range is appropriate (typically 0.01-0.1% of price)
- [ ] Volume range matches typical trading sizes
- [ ] Precision matches market conventions
- [ ] Multi-level depth is appropriate (10-20 for most uses)
- [ ] Publish probability is set intentionally
- [ ] Feed and venue names are set (or will auto-set)

## Related Documentation

- [Mock Market Data Feed Guide](MockMarketDataFeed.md)
- [Admin Commands Reference](AdminCommands.md)
- [Examples and Use Cases](Examples.md)
