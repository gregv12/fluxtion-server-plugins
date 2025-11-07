# Mock Market Data Feed Guide

## Overview

The `MockMarketDataFeedWorker` is a fully-featured simulated market data feed designed for development, testing, and demonstration purposes. It provides realistic market data generation with comprehensive runtime control capabilities.

## Table of Contents

- [Features](#features)
- [Architecture](#architecture)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [Admin Commands](#admin-commands)
- [Use Cases](#use-cases)
- [Best Practices](#best-practices)

## Features

### Core Capabilities

- **Single-Level Order Books**: Simple bid/ask with volume
- **Multi-Level Order Books**: Configurable depth with realistic liquidity distribution
- **Random Generation**: Price and volume within configurable ranges
- **Fixed Pricing**: Deterministic prices for testing
- **Runtime Control**: Start, stop, pause, resume without restart
- **Dynamic Configuration**: Add/remove symbols at runtime
- **JSON Configuration**: Import configurations from JSON
- **High Performance**: 1000+ updates per second capability

### State Management

The feed maintains three distinct states:

1. **Running**: Actively publishing market data
2. **Paused**: Halted but maintains configuration
3. **Stopped**: Halted with all configurations cleared

## Architecture

```
MockMarketDataFeedWorker
├── Scheduler Integration
│   └── Configurable publish rate (default: 1000ms)
├── Configuration Management
│   ├── configMap: Symbol → MarketDataBookConfig
│   └── marketDataBookConfigs: List of active configs
├── State Management
│   ├── running: boolean
│   └── paused: boolean
└── Admin Command Interface
    └── 13 runtime commands
```

## Getting Started

### Basic Setup

```java
// Create the feed worker
MockMarketDataFeedWorker mockFeed = new MockMarketDataFeedWorker();

// Set publish rate (optional, default is 1000ms)
mockFeed.setPublishRateMillis(500); // Publish every 500ms

// Create a simple configuration
MarketDataBookConfig config = new MarketDataBookConfig();
config.setSymbol("EURUSD");
config.setMinPrice(1.0800);
config.setMaxPrice(1.0900);
config.setMinSpread(0.0001);
config.setMaxSpread(0.0005);
config.setMinVolume(100000);
config.setMaxVolume(1000000);
config.setPublishProbability(1.0);

// Set the configuration
mockFeed.setMarketDataBookConfigs(List.of(config));

// Start the feed
mockFeed.start();
```

### With Fluxtion Server

```java
// The feed auto-registers with AdminCommandRegistry
// Commands will be available as: mockMarketDataFeed.<command>

// Access via admin interface:
// mockMarketDataFeed.getStatus
// mockMarketDataFeed.addConfig USDJPY 148.0 151.0 0.01 0.05 50000 500000
```

## Configuration

### MarketDataBookConfig Properties

#### Required Properties

| Property | Type | Description | Example |
|----------|------|-------------|---------|
| `symbol` | String | Trading symbol | "EURUSD" |
| `minPrice` | double | Minimum price for generation | 1.0800 |
| `maxPrice` | double | Maximum price for generation | 1.0900 |
| `minSpread` | double | Minimum bid-ask spread | 0.0001 |
| `maxSpread` | double | Maximum bid-ask spread | 0.0005 |
| `minVolume` | double | Minimum volume | 100000 |
| `maxVolume` | double | Maximum volume | 1000000 |

#### Optional Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `feedName` | String | Auto-set | Feed identifier |
| `venueName` | String | Auto-set | Venue identifier |
| `publishProbability` | double | 1.0 | Probability of publishing (0.0-1.0) |
| `precisionDpsPrice` | int | 3 | Decimal places for prices |
| `precisionDpsVolume` | int | 3 | Decimal places for volumes |
| `multilevel` | boolean | false | Enable multi-level book |
| `multilevelDepth` | int | 20 | Number of price levels |
| `multilevelBookConfig` | MultilevelBookConfig | default | Multi-level book settings |

### Configuration Methods

#### Method 1: Programmatic Configuration

```java
MarketDataBookConfig config = new MarketDataBookConfig();
config.setSymbol("GBPUSD");
config.setMinPrice(1.2400);
config.setMaxPrice(1.2600);
config.setMinSpread(0.0002);
config.setMaxSpread(0.0008);
config.setMinVolume(50000);
config.setMaxVolume(500000);
config.setPublishProbability(0.8); // Publish 80% of the time

// For multi-level books
config.setMultilevel(true);
config.setMultilevelDepth(10);

mockFeed.setMarketDataBookConfigs(List.of(config));
```

#### Method 2: Admin Command

```bash
# Add via admin interface
mockMarketDataFeed.addConfig GBPUSD 1.2400 1.2600 0.0002 0.0008 50000 500000
```

#### Method 3: JSON Configuration

```bash
# Configure from JSON
mockMarketDataFeed.configureFromJson {
  "symbol": "GBPUSD",
  "minPrice": 1.2400,
  "maxPrice": 1.2600,
  "minSpread": 0.0002,
  "maxSpread": 0.0008,
  "minVolume": 50000,
  "maxVolume": 500000,
  "publishProbability": 1.0,
  "multilevel": false
}
```

### Single-Level vs Multi-Level

#### Single-Level Order Book

Generates simple bid/ask quotes:

```
BID: 1.0850 @ 250000
ASK: 1.0853 @ 320000
```

**Configuration:**
```java
config.setMultilevel(false); // Default
```

**Use Cases:**
- Simple pricing models
- Basic strategy testing
- High-frequency updates

#### Multi-Level Order Book

Generates full order book depth:

```
BID SIDE                    ASK SIDE
1.0850 @ 500000  (5)       1.0853 @ 450000  (8)
1.0849 @ 500000  (10)      1.0854 @ 500000  (12)
1.0848 @ 333000  (7)       1.0855 @ 333000  (10)
...
```

**Configuration:**
```java
config.setMultilevel(true);
config.setMultilevelDepth(10); // 10 levels per side

MultilevelBookConfig bookConfig = MultilevelBookConfig.builder()
    .maxDepth(20)
    .build();
config.setMultilevelBookConfig(bookConfig);
```

**Use Cases:**
- Market impact analysis
- Liquidity-aware strategies
- Smart order routing testing
- Volume-weighted average price (VWAP) calculations

## Admin Commands

### Quick Reference

```bash
# Control
mockMarketDataFeed.start
mockMarketDataFeed.stop
mockMarketDataFeed.pause
mockMarketDataFeed.resume

# Status
mockMarketDataFeed.getStatus
mockMarketDataFeed.listConfigs

# Configuration
mockMarketDataFeed.addConfig SYMBOL MIN_PRICE MAX_PRICE MIN_SPREAD MAX_SPREAD MIN_VOL MAX_VOL
mockMarketDataFeed.removeConfig SYMBOL
mockMarketDataFeed.clearAll

# Fixed Pricing
mockMarketDataFeed.setFixedSingleLevel SYMBOL BID_PRICE BID_VOL ASK_PRICE ASK_VOL
mockMarketDataFeed.setFixedMultiLevel SYMBOL MID_PRICE SPREAD VOLUME DEPTH TICK_SIZE MAX_QTY

# Runtime
mockMarketDataFeed.setPublishRate MILLIS

# Advanced
mockMarketDataFeed.configureFromJson {...}
```

[Full command reference →](AdminCommands.md)

## Use Cases

### Development and Testing

#### 1. Unit Testing with Fixed Prices

```bash
# Set deterministic prices for predictable tests
mockMarketDataFeed.setFixedSingleLevel EURUSD 1.0850 100000 1.0853 100000
```

#### 2. Integration Testing with Random Prices

```bash
# Add realistic random variation
mockMarketDataFeed.addConfig EURUSD 1.0800 1.0900 0.0001 0.0005 100000 1000000
```

#### 3. Strategy Backtesting

```java
// Configure multiple symbols
for (String symbol : tradingUniverse) {
    MarketDataBookConfig config = createRealisticConfig(symbol);
    config.setPublishProbability(0.9); // 90% to simulate gaps
    configs.add(config);
}
mockFeed.setMarketDataBookConfigs(configs);
```

### Performance Testing

#### Load Testing

```bash
# Set high-frequency updates
mockMarketDataFeed.setPublishRate 10  # 100 updates/second

# Add multiple symbols
mockMarketDataFeed.addConfig EUR USD 1.08 1.09 0.0001 0.0005 100000 1000000
mockMarketDataFeed.addConfig GBPUSD 1.24 1.26 0.0002 0.0008 50000 500000
mockMarketDataFeed.addConfig USDJPY 148.0 151.0 0.01 0.05 10000 100000
```

#### Stress Testing

```java
// Configure 100+ symbols with multilevel books
List<MarketDataBookConfig> configs = new ArrayList<>();
for (int i = 0; i < 100; i++) {
    MarketDataBookConfig config = createConfig("PAIR" + i);
    config.setMultilevel(true);
    config.setMultilevelDepth(20);
    config.setPublishProbability(1.0);
    configs.add(config);
}
mockFeed.setMarketDataBookConfigs(configs);
mockFeed.setPublishRateMillis(10); // Very aggressive
```

### Demo and Training

#### Realistic Market Simulation

```bash
# Start with realistic FX pairs
mockMarketDataFeed.addConfig EURUSD 1.0800 1.0900 0.0001 0.0005 100000 1000000
mockMarketDataFeed.addConfig GBPUSD 1.2400 1.2600 0.0002 0.0008 50000 500000
mockMarketDataFeed.addConfig USDJPY 148.0 151.0 0.01 0.05 10000 100000
mockMarketDataFeed.addConfig AUDUSD 0.6400 0.6600 0.0001 0.0004 80000 800000

# Simulate realistic update rates
mockMarketDataFeed.setPublishRate 2000  # Every 2 seconds
```

#### Live Demo Control

```bash
# Pause during explanation
mockMarketDataFeed.pause

# Resume for demonstration
mockMarketDataFeed.resume

# Switch to fixed prices for clarity
mockMarketDataFeed.setFixedSingleLevel EURUSD 1.0850 250000 1.0853 250000
```

## Best Practices

### 1. Match Real Market Characteristics

```java
// Use realistic spreads for the instrument class
config.setMinSpread(0.0001); // Tight for major FX
config.setMaxSpread(0.0005);

// Match typical volumes
config.setMinVolume(100000); // Standard lot
config.setMaxVolume(1000000); // Large order
```

### 2. Use Publish Probability for Realism

```java
// Not every tick has a quote - simulate gaps
config.setPublishProbability(0.8); // 20% gap rate
```

### 3. Adjust Publish Rate by Use Case

| Use Case | Recommended Rate | Rationale |
|----------|------------------|-----------|
| Development | 1000-2000ms | Easy to observe |
| Integration Testing | 100-500ms | Realistic pace |
| Performance Testing | 10-50ms | Stress conditions |
| Demo/Training | 2000-5000ms | Easy to follow |

### 4. Multi-Level for Liquidity-Aware Testing

```java
// Use multi-level when testing:
// - Market impact
// - Liquidity analysis
// - Smart order routing
// - VWAP/TWAP algorithms

config.setMultilevel(true);
config.setMultilevelDepth(10); // Sufficient for most analyses
```

### 5. Clean State Management

```bash
# Clear all configs before major changes
mockMarketDataFeed.stop  # Stops and clears

# Or clear without stopping
mockMarketDataFeed.clearAll

# Then reconfigure
mockMarketDataFeed.start
```

### 6. Monitor Status

```bash
# Regularly check status during development
mockMarketDataFeed.getStatus

# Output shows:
# - Running state
# - Paused state
# - Publish rate
# - Configured symbols
```

## Troubleshooting

### Feed Not Publishing

**Check:**
1. Is the feed running? `mockMarketDataFeed.getStatus`
2. Is the feed paused? Look for `Paused: true`
3. Are there configured symbols? `mockMarketDataFeed.listConfigs`
4. Check publish probability (< 1.0 means intermittent publishing)

**Solution:**
```bash
mockMarketDataFeed.start
mockMarketDataFeed.resume  # if paused
```

### No Market Data for Symbol

**Check:**
```bash
mockMarketDataFeed.listConfigs
```

**Solution:**
```bash
# Add the missing symbol
mockMarketDataFeed.addConfig SYMBOL 100 110 0.01 0.05 1000 10000
```

### Unexpected Prices

**Issue:** Prices outside expected range

**Solution:** Check configuration:
```bash
mockMarketDataFeed.listConfigs
# Review minPrice, maxPrice, minSpread, maxSpread
```

**Fix with precise prices:**
```bash
mockMarketDataFeed.setFixedSingleLevel EURUSD 1.0850 100000 1.0853 100000
```

### Performance Issues

**Symptoms:** High CPU, delayed updates

**Check:**
- Number of symbols
- Publish rate
- Multi-level depth

**Solutions:**
```bash
# Reduce publish rate
mockMarketDataFeed.setPublishRate 100  # Slower

# Remove unused symbols
mockMarketDataFeed.removeConfig UNUSED_SYMBOL

# Use single-level for high-frequency scenarios
# (Configure with multilevel: false)
```

## Advanced Topics

### Custom Book Configurations

```java
// Create custom multi-level book behavior
MultilevelBookConfig bookConfig = MultilevelBookConfig.builder()
    .maxDepth(50)
    .build();

config.setMultilevelBookConfig(bookConfig);
```

### Dynamic Reconfiguration

```java
// Change configuration at runtime without stopping
mockFeed.getMarketDataBookConfigs().clear();
mockFeed.setMarketDataBookConfigs(newConfigs);
```

### Integration with Other Services

```java
// The feed publishes MarketFeedEvent objects
// that can be consumed by:
// - Market data aggregators
// - Pricing engines
// - Order management systems
// - Risk management systems
```

## Related Documentation

- [Admin Commands Reference](AdminCommands.md)
- [Configuration Guide](Configuration.md)
- [Examples and Use Cases](Examples.md)

---

**Need Help?**
- Check the [Examples](Examples.md) for common scenarios
- Review [Admin Commands](AdminCommands.md) for command details
- See [Configuration Guide](Configuration.md) for advanced options
