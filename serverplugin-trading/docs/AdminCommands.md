# Admin Commands Reference

Complete reference for MockMarketDataFeedWorker admin commands.

## Table of Contents

- [Command Overview](#command-overview)
- [Control Commands](#control-commands)
- [Status Commands](#status-commands)
- [Configuration Commands](#configuration-commands)
- [Fixed Pricing Commands](#fixed-pricing-commands)
- [Runtime Commands](#runtime-commands)
- [Advanced Commands](#advanced-commands)
- [Error Handling](#error-handling)
- [Command Examples](#command-examples)

## Command Overview

All commands follow the pattern:
```
<feedName>.<commandName> [arguments...]
```

Default feed name: `mockMarketDataFeed`

### Command Categories

| Category | Commands | Purpose |
|----------|----------|---------|
| **Control** | start, stop, pause, resume | Feed lifecycle management |
| **Status** | getStatus, listConfigs | Inspect current state |
| **Configuration** | addConfig, removeConfig, clearAll | Symbol management |
| **Fixed Pricing** | setFixedSingleLevel, setFixedMultiLevel | Deterministic pricing |
| **Runtime** | setPublishRate | Adjust behavior |
| **Advanced** | configureFromJson | Bulk configuration |

## Control Commands

### start

**Description:** Starts the mock market data feed and resumes publishing.

**Syntax:**
```bash
mockMarketDataFeed.start
```

**Arguments:** None

**Effects:**
- Sets `running = true`
- Sets `paused = false`
- Sends `MarketConnected` event
- Begins publishing configured market data

**Example:**
```bash
mockMarketDataFeed.start
# Output: Mock market data feed started
```

**State Transitions:**
- Stopped → Running
- Paused → Running

---

### stop

**Description:** Stops the feed and clears all configurations.

**Syntax:**
```bash
mockMarketDataFeed.stop
```

**Arguments:** None

**Effects:**
- Sets `running = false`
- Sets `paused = false`
- Clears all `marketDataBookConfigs`
- Clears `configMap`

**Example:**
```bash
mockMarketDataFeed.stop
# Output: Mock market data feed stopped and all configs cleared
```

**Warning:** This command removes all symbol configurations. Use `pause` to temporarily halt without losing configuration.

**State Transitions:**
- Running → Stopped
- Paused → Stopped

---

### pause

**Description:** Temporarily halts publishing without clearing configurations.

**Syntax:**
```bash
mockMarketDataFeed.pause
```

**Arguments:** None

**Effects:**
- Sets `paused = true`
- Maintains all configurations
- Stops publishing until resumed

**Example:**
```bash
mockMarketDataFeed.pause
# Output: Mock market data feed paused
```

**Error Conditions:**
- If feed is not running: "Feed is not running - cannot pause"

**State Transitions:**
- Running → Paused

---

### resume

**Description:** Resumes publishing after a pause.

**Syntax:**
```bash
mockMarketDataFeed.resume
```

**Arguments:** None

**Effects:**
- Sets `paused = false`
- Resumes publishing with existing configurations

**Example:**
```bash
mockMarketDataFeed.resume
# Output: Mock market data feed resumed
```

**Error Conditions:**
- If feed is not running: "Feed is not running - use start command instead"

**State Transitions:**
- Paused → Running

---

## Status Commands

### getStatus

**Description:** Displays comprehensive feed status information.

**Syntax:**
```bash
mockMarketDataFeed.getStatus
```

**Arguments:** None

**Output Format:**
```
Mock Market Data Feed Status
============================================================
Feed Name: mockMarketDataFeed
Running: true
Paused: false
Publish Rate: 1000 ms
Configured Symbols: 3

Symbols:
  - EURUSD (single-level)
  - GBPUSD (multilevel-10)
  - USDJPY (single-level)
============================================================
```

**Information Displayed:**
- Feed name
- Running state
- Paused state
- Current publish rate (ms)
- Number of configured symbols
- List of symbols with their type

**Example:**
```bash
mockMarketDataFeed.getStatus
```

---

### listConfigs

**Description:** Lists detailed configuration for all symbols.

**Syntax:**
```bash
mockMarketDataFeed.listConfigs
```

**Arguments:** None

**Output Format:**
```
Market Data Book Configurations
================================================================================
Symbol: EURUSD
  Type: Single-level
  Price Range: 1.080000 - 1.090000
  Spread Range: 0.000100 - 0.000500
  Volume Range: 100000.00 - 1000000.00
  Publish Probability: 1.00

Symbol: GBPUSD
  Type: Multi-level
  Price Range: 1.240000 - 1.260000
  Spread Range: 0.000200 - 0.000800
  Volume Range: 50000.00 - 500000.00
  Publish Probability: 0.80
  Depth: 10 levels
```

**Example:**
```bash
mockMarketDataFeed.listConfigs
```

**Empty Case:**
```bash
mockMarketDataFeed.listConfigs
# Output: No market data book configurations
```

---

## Configuration Commands

### addConfig

**Description:** Adds or updates a symbol configuration with random price generation.

**Syntax:**
```bash
mockMarketDataFeed.addConfig SYMBOL MIN_PRICE MAX_PRICE MIN_SPREAD MAX_SPREAD MIN_VOLUME MAX_VOLUME
```

**Arguments:**

| Position | Name | Type | Description |
|----------|------|------|-------------|
| 1 | SYMBOL | String | Trading symbol |
| 2 | MIN_PRICE | double | Minimum price |
| 3 | MAX_PRICE | double | Maximum price |
| 4 | MIN_SPREAD | double | Minimum spread |
| 5 | MAX_SPREAD | double | Maximum spread |
| 6 | MIN_VOLUME | double | Minimum volume |
| 7 | MAX_VOLUME | double | Maximum volume |

**Defaults:**
- `publishProbability`: 1.0 (always publish)
- `multilevel`: false
- `feedName`: Current feed name
- `venueName`: Current feed name

**Examples:**
```bash
# EUR/USD with tight spreads
mockMarketDataFeed.addConfig EURUSD 1.0800 1.0900 0.0001 0.0005 100000 1000000

# GBP/USD with wider spreads
mockMarketDataFeed.addConfig GBPUSD 1.2400 1.2600 0.0002 0.0008 50000 500000

# USD/JPY
mockMarketDataFeed.addConfig USDJPY 148.0 151.0 0.01 0.05 10000 100000
```

**Effect:**
- Replaces existing configuration if symbol exists
- Adds to `marketDataBookConfigs` list
- Updates `configMap`

**Error Conditions:**
- Missing arguments: "addConfig requires 7 arguments: [symbol] [minPrice] [maxPrice] [minSpread] [maxSpread] [minVolume] [maxVolume]"
- Invalid number format: "Invalid number format in arguments: ..."

---

### removeConfig

**Description:** Removes configuration for a specific symbol.

**Syntax:**
```bash
mockMarketDataFeed.removeConfig SYMBOL
```

**Arguments:**

| Position | Name | Type | Description |
|----------|------|------|-------------|
| 1 | SYMBOL | String | Symbol to remove |

**Examples:**
```bash
mockMarketDataFeed.removeConfig EURUSD
# Output: Removed config for symbol: EURUSD
```

**Error Conditions:**
- Symbol not found: "No config found for symbol: SYMBOL"
- Missing argument: "removeConfig requires 1 argument [symbol]"

---

### clearAll

**Description:** Removes all symbol configurations.

**Syntax:**
```bash
mockMarketDataFeed.clearAll
```

**Arguments:** None

**Example:**
```bash
mockMarketDataFeed.clearAll
# Output: Cleared all 5 market data book configurations
```

**Note:** Does not stop the feed. Use `stop` to stop and clear in one command.

---

## Fixed Pricing Commands

### setFixedSingleLevel

**Description:** Sets a symbol with fixed single-level prices (no randomization).

**Syntax:**
```bash
mockMarketDataFeed.setFixedSingleLevel SYMBOL BID_PRICE BID_VOLUME ASK_PRICE ASK_VOLUME
```

**Arguments:**

| Position | Name | Type | Description |
|----------|------|------|-------------|
| 1 | SYMBOL | String | Trading symbol |
| 2 | BID_PRICE | double | Fixed bid price |
| 3 | BID_VOLUME | double | Fixed bid volume |
| 4 | ASK_PRICE | double | Fixed ask price |
| 5 | ASK_VOLUME | double | Fixed ask volume |

**Configuration Details:**
- `minPrice = maxPrice = bidPrice` (no variation)
- `minSpread = maxSpread = askPrice - bidPrice` (fixed spread)
- `publishProbability = 1.0` (always publish)
- `multilevel = false`

**Examples:**
```bash
# Fixed EUR/USD for testing
mockMarketDataFeed.setFixedSingleLevel EURUSD 1.0850 250000 1.0853 250000

# Fixed GBP/USD with different volumes
mockMarketDataFeed.setFixedSingleLevel GBPUSD 1.2500 100000 1.2510 150000

# Fixed USD/JPY
mockMarketDataFeed.setFixedSingleLevel USDJPY 150.00 50000 150.05 50000
```

**Use Cases:**
- Unit testing with predictable prices
- Demo scenarios requiring stable quotes
- Debugging price-sensitive logic

**Error Conditions:**
- Missing arguments: "setFixedSingleLevel requires 5 arguments: [symbol] [bidPrice] [bidVolume] [askPrice] [askVolume]"
- Invalid number format: "Invalid number format in arguments: ..."

---

### setFixedMultiLevel

**Description:** Sets a symbol with fixed multi-level order book.

**Syntax:**
```bash
mockMarketDataFeed.setFixedMultiLevel SYMBOL MID_PRICE SPREAD VOLUME DEPTH TICK_SIZE MAX_QUANTITY_ON_SIDE
```

**Arguments:**

| Position | Name | Type | Description |
|----------|------|------|-------------|
| 1 | SYMBOL | String | Trading symbol |
| 2 | MID_PRICE | double | Mid-market price |
| 3 | SPREAD | double | Bid-ask spread |
| 4 | VOLUME | double | Base volume per level |
| 5 | DEPTH | int | Number of levels per side |
| 6 | TICK_SIZE | double | Price increment between levels |
| 7 | MAX_QUANTITY_ON_SIDE | double | Maximum total quantity (for future use) |

**Configuration Details:**
- `minPrice = maxPrice = midPrice - (spread/2)`
- `minSpread = maxSpread = spread`
- Volume varies: `baseVolume * 0.8` to `baseVolume * 1.2`
- `multilevel = true`
- `multilevelDepth = depth`
- `publishProbability = 1.0`

**Examples:**
```bash
# 10-level EUR/USD book
mockMarketDataFeed.setFixedMultiLevel EURUSD 1.0850 0.0003 500000 10 0.0001 10000000

# 20-level BTC/USD book
mockMarketDataFeed.setFixedMultiLevel BTCUSD 50000 10 1000000 20 5 100000000

# 5-level GBP/USD book
mockMarketDataFeed.setFixedMultiLevel GBPUSD 1.2500 0.0010 200000 5 0.0002 5000000
```

**Resulting Book Structure:**
```
Example: EURUSD 1.0850 0.0003 500000 10 0.0001

BID SIDE                        ASK SIDE
1.08485 @ 500K (levels 0)      1.08515 @ 500K
1.08475 @ 500K (level 1)       1.08525 @ 500K
1.08465 @ 333K (level 2)       1.08535 @ 333K
...
```

**Use Cases:**
- Testing market impact algorithms
- Liquidity analysis
- Smart order routing development
- VWAP/TWAP calculations

**Error Conditions:**
- Missing arguments: "setFixedMultiLevel requires at least 7 arguments: [symbol] [midPrice] [spread] [volume] [depth] [tickSize] [maxQuantityOnSide]"
- Invalid number format: "Invalid number format in arguments: ..."

---

## Runtime Commands

### setPublishRate

**Description:** Changes the market data publishing rate.

**Syntax:**
```bash
mockMarketDataFeed.setPublishRate RATE_MILLIS
```

**Arguments:**

| Position | Name | Type | Description | Min |
|----------|------|------|-------------|-----|
| 1 | RATE_MILLIS | int | Publish interval in milliseconds | 1 |

**Examples:**
```bash
# Slow rate for observation
mockMarketDataFeed.setPublishRate 2000  # Every 2 seconds

# Medium rate for testing
mockMarketDataFeed.setPublishRate 500   # Every 500ms

# High frequency for load testing
mockMarketDataFeed.setPublishRate 10    # Every 10ms (100/sec)

# Default rate
mockMarketDataFeed.setPublishRate 1000  # Every 1 second
```

**Output:**
```
Publish rate changed from 1000ms to 500ms
```

**Performance Guidelines:**

| Rate (ms) | Frequency | Use Case |
|-----------|-----------|----------|
| 5000+ | ≤ 0.2/sec | Demo, training |
| 1000-5000 | 0.2-1/sec | Development |
| 100-1000 | 1-10/sec | Testing |
| 10-100 | 10-100/sec | Load testing |
| < 10 | > 100/sec | Stress testing |

**Error Conditions:**
- Missing argument: "setPublishRate requires 1 argument [rateMillis]"
- Invalid number: "Invalid number format for publish rate: ..."
- Rate < 1: "Publish rate must be positive"

**Note:** Changes take effect on the next scheduled publish cycle.

---

## Advanced Commands

### configureFromJson

**Description:** Configures a symbol from JSON representation of MarketDataBookConfig.

**Syntax:**
```bash
mockMarketDataFeed.configureFromJson JSON_CONFIG
```

**Arguments:**

| Position | Name | Type | Description |
|----------|------|------|-------------|
| 1+ | JSON_CONFIG | JSON | Complete JSON configuration |

**JSON Schema:**
```json
{
  "symbol": "string (required)",
  "minPrice": "number (required)",
  "maxPrice": "number (required)",
  "minSpread": "number (required)",
  "maxSpread": "number (required)",
  "minVolume": "number (required)",
  "maxVolume": "number (required)",
  "feedName": "string (optional)",
  "venueName": "string (optional)",
  "publishProbability": "number (default: 1.0)",
  "precisionDpsPrice": "number (default: 3)",
  "precisionDpsVolume": "number (default: 3)",
  "multilevel": "boolean (default: false)",
  "multilevelDepth": "number (default: 20)",
  "multilevelBookConfig": "object (optional)"
}
```

**Examples:**

**Single-Level Configuration:**
```bash
mockMarketDataFeed.configureFromJson {
  "symbol": "EURUSD",
  "minPrice": 1.0800,
  "maxPrice": 1.0900,
  "minSpread": 0.0001,
  "maxSpread": 0.0005,
  "minVolume": 100000,
  "maxVolume": 1000000,
  "publishProbability": 0.9
}
```

**Multi-Level Configuration:**
```bash
mockMarketDataFeed.configureFromJson {
  "symbol": "GBPUSD",
  "minPrice": 1.2400,
  "maxPrice": 1.2600,
  "minSpread": 0.0002,
  "maxSpread": 0.0008,
  "minVolume": 50000,
  "maxVolume": 500000,
  "publishProbability": 1.0,
  "multilevel": true,
  "multilevelDepth": 15,
  "precisionDpsPrice": 5,
  "precisionDpsVolume": 2
}
```

**Use Cases:**
- Bulk configuration from external source
- Configuration management as code
- Import from configuration files
- Complex configurations requiring all options

**Auto-Set Fields:**
- If `feedName` not provided: Uses current feed name
- If `venueName` not provided: Uses current feed name

**Error Conditions:**
- Invalid JSON: "Failed to parse JSON configuration: ..."
- Missing required fields: JSON parsing error

**Note:** Spaces in JSON are supported as the command reconstructs the full JSON from all arguments.

---

## Error Handling

### Common Error Messages

#### Invalid Arguments

```bash
mockMarketDataFeed.addConfig EURUSD
# Error: addConfig requires 7 arguments: [symbol] [minPrice] [maxPrice] [minSpread] [maxSpread] [minVolume] [maxVolume]
```

#### Invalid Number Format

```bash
mockMarketDataFeed.setPublishRate abc
# Error: Invalid number format for publish rate: abc
```

#### State Validation Errors

```bash
# When feed is not running
mockMarketDataFeed.pause
# Error: Feed is not running - cannot pause

mockMarketDataFeed.resume
# Error: Feed is not running - use start command instead
```

#### Symbol Not Found

```bash
mockMarketDataFeed.removeConfig UNKNOWN
# Error: No config found for symbol: UNKNOWN
```

### Error Response Channels

- **Errors** go to `err` consumer (typically stderr)
- **Success messages** go to `out` consumer (typically stdout)

### Validation Rules

1. **Publish Rate:** Must be ≥ 1
2. **State Transitions:** Must follow valid state machine
3. **Required Arguments:** All required arguments must be provided
4. **Number Formats:** Must be valid double/int representations

---

## Command Examples

### Workflow 1: Development Setup

```bash
# Start fresh
mockMarketDataFeed.stop
mockMarketDataFeed.start

# Add major FX pairs
mockMarketDataFeed.addConfig EURUSD 1.0800 1.0900 0.0001 0.0005 100000 1000000
mockMarketDataFeed.addConfig GBPUSD 1.2400 1.2600 0.0002 0.0008 50000 500000
mockMarketDataFeed.addConfig USDJPY 148.0 151.0 0.01 0.05 10000 100000

# Set reasonable update rate
mockMarketDataFeed.setPublishRate 1000

# Check configuration
mockMarketDataFeed.listConfigs
```

### Workflow 2: Testing with Fixed Prices

```bash
# Set deterministic prices
mockMarketDataFeed.setFixedSingleLevel EURUSD 1.0850 250000 1.0853 250000
mockMarketDataFeed.setFixedSingleLevel GBPUSD 1.2500 100000 1.2510 100000

# Verify
mockMarketDataFeed.getStatus
mockMarketDataFeed.listConfigs
```

### Workflow 3: Load Testing

```bash
# Clear and start fresh
mockMarketDataFeed.stop
mockMarketDataFeed.start

# Add multiple symbols
mockMarketDataFeed.addConfig PAIR1 100 110 0.01 0.05 1000 10000
mockMarketDataFeed.addConfig PAIR2 200 210 0.01 0.05 1000 10000
mockMarketDataFeed.addConfig PAIR3 300 310 0.01 0.05 1000 10000
# ... add more symbols

# Set aggressive rate
mockMarketDataFeed.setPublishRate 10

# Monitor
mockMarketDataFeed.getStatus
```

### Workflow 4: Demo Control

```bash
# Start with fixed prices for clarity
mockMarketDataFeed.setFixedSingleLevel EURUSD 1.0850 250000 1.0853 250000

# Slow updates for visibility
mockMarketDataFeed.setPublishRate 3000

# Pause during explanation
mockMarketDataFeed.pause

# Resume for demonstration
mockMarketDataFeed.resume

# Switch to random for realistic behavior
mockMarketDataFeed.addConfig EURUSD 1.0800 1.0900 0.0001 0.0005 100000 1000000
```

### Workflow 5: Multi-Level Testing

```bash
# Configure multi-level book
mockMarketDataFeed.setFixedMultiLevel EURUSD 1.0850 0.0003 500000 10 0.0001 10000000

# Verify depth
mockMarketDataFeed.listConfigs

# Can also do via JSON
mockMarketDataFeed.configureFromJson {
  "symbol": "GBPUSD",
  "minPrice": 1.2500,
  "maxPrice": 1.2500,
  "minSpread": 0.0010,
  "maxSpread": 0.0010,
  "minVolume": 200000,
  "maxVolume": 200000,
  "multilevel": true,
  "multilevelDepth": 15
}
```

---

## Quick Reference Card

```
CONTROL
  start                              Start feed
  stop                               Stop and clear all
  pause                              Pause publishing
  resume                             Resume publishing

STATUS
  getStatus                          Show feed status
  listConfigs                        List all configurations

CONFIGURATION
  addConfig SYM MIN MAX MINSP MAXSP MINV MAXV   Add/update config
  removeConfig SYMBOL                Remove config
  clearAll                           Clear all configs

FIXED PRICING
  setFixedSingleLevel SYM BP BV AP AV    Fixed single-level
  setFixedMultiLevel SYM MID SPR VOL D T MAX    Fixed multi-level

RUNTIME
  setPublishRate MILLIS              Change publish rate

ADVANCED
  configureFromJson {...}            JSON configuration
```

---

## Related Documentation

- [Mock Market Data Feed Guide](MockMarketDataFeed.md)
- [Configuration Guide](Configuration.md)
- [Examples and Use Cases](Examples.md)
