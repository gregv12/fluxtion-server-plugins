# Fluxtion Server Plugin - Trading

A comprehensive trading plugin for the Fluxtion Server platform that provides market data feeds, order execution, and trading venue integration capabilities.

## Overview

The `serverplugin-trading` module provides essential trading infrastructure components including:

- **Market Data Feeds**: Real-time and simulated market data publishing
- **Order Execution**: Order management and execution services
- **QuickFIX/J Integration**: FIX protocol support for industry-standard connectivity
- **Mock Trading Venues**: Simulated trading environments for development and testing
- **Replay Capabilities**: Historical market data replay from PostgreSQL

## Key Components

### Market Data Feeds

#### MockMarketDataFeedWorker
A fully-featured mock market data feed with runtime configuration capabilities.

**Features:**
- Single-level and multi-level order book simulation
- Configurable price ranges, spreads, and volumes
- Runtime control via admin commands (start, stop, pause, resume)
- JSON-based configuration
- Customizable publish rates
- Fixed or random price generation

**Use Cases:**
- Development and testing without live market connections
- Load testing and performance validation
- Demo and training environments
- Integration testing

[Learn more →](docs/MockMarketDataFeed.md)

#### QuickFixMarketDataFeed
FIX protocol-based market data feed for real venue connectivity.

### Order Execution

#### MockOrderExecutor
Simulated order execution for testing trading strategies.

#### QuickFixOrderVenue
FIX protocol-based order execution for real venue connectivity.

### Replay Components

#### PostgresMarketDataReplay
Replay historical market data from PostgreSQL database for backtesting and analysis.

## Getting Started

### Dependencies

Add to your `pom.xml`:

```xml
<dependency>
    <groupId>com.fluxtion</groupId>
    <artifactId>serverplugin-trading</artifactId>
    <version>0.2.11-SNAPSHOT</version>
</dependency>
```

### Basic Usage

#### 1. Configure a Mock Market Data Feed

```java
MockMarketDataFeedWorker mockFeed = new MockMarketDataFeedWorker();
mockFeed.setPublishRateMillis(1000); // Publish every second

// Add a simple configuration
MarketDataBookConfig config = new MarketDataBookConfig();
config.setSymbol("EURUSD");
config.setMinPrice(1.0800);
config.setMaxPrice(1.0900);
config.setMinSpread(0.0001);
config.setMaxSpread(0.0005);
config.setMinVolume(100000);
config.setMaxVolume(1000000);
config.setPublishProbability(1.0);

mockFeed.setMarketDataBookConfigs(List.of(config));
```

#### 2. Use Admin Commands at Runtime

Access the admin interface and control the feed:

```bash
# Check status
mockMarketDataFeed.getStatus

# Add a new symbol
mockMarketDataFeed.addConfig USDJPY 148.0 151.0 0.01 0.05 50000 500000

# Set fixed prices for testing
mockMarketDataFeed.setFixedSingleLevel GBPUSD 1.2500 100000 1.2510 100000

# Pause/resume
mockMarketDataFeed.pause
mockMarketDataFeed.resume

# Adjust publish rate
mockMarketDataFeed.setPublishRate 500
```

## Documentation

### Guides

- [Mock Market Data Feed Guide](docs/MockMarketDataFeed.md) - Complete guide to using the mock market data feed
- [Configuration Guide](docs/Configuration.md) - Detailed configuration options
- [Admin Commands Reference](docs/AdminCommands.md) - Complete command reference
- [Examples and Use Cases](docs/Examples.md) - Practical examples and patterns

### API Documentation

- [Market Data Services](docs/api/MarketDataServices.md)
- [Order Services](docs/api/OrderServices.md)

## Architecture

```
serverplugin-trading/
├── component/
│   ├── marketdatafeed/        # Market data feed abstractions
│   ├── mockvenue/              # Mock trading venue implementations
│   │   ├── mktdata/           # Mock market data components
│   │   ├── orders/            # Mock order execution
│   │   └── mktmaking/         # Mock market making
│   ├── quickfixj/             # QuickFIX/J integrations
│   ├── tradevenue/            # Trading venue abstractions
│   └── replay/                # Market data replay components
```

## Admin Command Overview

The mock market data feed provides comprehensive runtime control:

| Category | Commands | Description |
|----------|----------|-------------|
| **Control** | start, stop, pause, resume | Feed lifecycle management |
| **Configuration** | addConfig, removeConfig, clearAll | Symbol configuration |
| **Fixed Pricing** | setFixedSingleLevel, setFixedMultiLevel | Set deterministic prices |
| **Status** | getStatus, listConfigs | View current state |
| **Runtime** | setPublishRate | Adjust publish frequency |
| **Advanced** | configureFromJson | JSON-based configuration |

[See full command reference →](docs/AdminCommands.md)

## Examples

### Example 1: Testing a Market Making Strategy

```java
// Setup mock feed with realistic EUR/USD prices
MarketDataBookConfig eurusd = new MarketDataBookConfig();
eurusd.setSymbol("EURUSD");
eurusd.setMinPrice(1.0850);
eurusd.setMaxPrice(1.0880);
eurusd.setMinSpread(0.0001);
eurusd.setMaxSpread(0.0003);
eurusd.setMinVolume(100000);
eurusd.setMaxVolume(1000000);
eurusd.setPublishProbability(0.8); // 80% publish rate for realistic gaps

mockFeed.setMarketDataBookConfigs(List.of(eurusd));
mockFeed.start();
```

### Example 2: Multi-Level Order Book Simulation

```bash
# Configure 10-level order book via admin command
mockMarketDataFeed.setFixedMultiLevel BTCUSD 50000 10 1000000 10 5 10000000

# Result: 10-level book centered at 50K with 10 spread, 1M base volume
```

### Example 3: Load Testing

```java
// Create multiple symbols with high-frequency updates
for (String symbol : symbols) {
    MarketDataBookConfig config = createConfig(symbol);
    config.setPublishProbability(1.0); // Always publish
    configs.add(config);
}

mockFeed.setMarketDataBookConfigs(configs);
mockFeed.setPublishRateMillis(10); // 100 updates/second
mockFeed.start();
```

[More examples →](docs/Examples.md)

## Testing

The plugin includes comprehensive test coverage for all components.

```bash
mvn test
```

## Features by Component

### MockMarketDataFeedWorker

✅ Single-level order books
✅ Multi-level order books (configurable depth)
✅ Random price generation with constraints
✅ Fixed price mode for deterministic testing
✅ Runtime configuration via admin commands
✅ JSON configuration support
✅ Pause/resume without data loss
✅ Dynamic publish rate adjustment
✅ Per-symbol configuration

### QuickFix Components

✅ FIX 4.4 protocol support
✅ Market data subscription
✅ Order execution
✅ Session management
✅ Message parsing and routing

### Replay Components

✅ PostgreSQL data source
✅ Time-based replay
✅ Rate control
✅ Multiple symbol support

## Performance Characteristics

| Metric | Value |
|--------|-------|
| Max symbols | 1000+ |
| Max publish rate | 1000+ updates/second |
| Latency (mock) | < 1ms |
| Memory per symbol | ~1KB |

## Requirements

- Java 21 or higher
- Fluxtion Server 0.2.10+
- QuickFIX/J 2.3.2+ (for FIX components)
- PostgreSQL 12+ (for replay components)

## Contributing

Contributions are welcome! Please see the main Fluxtion Server repository for contribution guidelines.

## License

Licensed under the Apache License 2.0. See LICENSE file for details.

## Support

- Documentation: [docs/](docs/)
- Issues: GitHub Issues
- Community: Fluxtion Community Forum

## Changelog

### Version 0.2.11-SNAPSHOT

**New Features:**
- ✨ Comprehensive admin command suite for MockMarketDataFeedWorker
- ✨ Runtime control (start, stop, pause, resume)
- ✨ Fixed single-level and multi-level pricing modes
- ✨ JSON configuration support
- ✨ Dynamic publish rate adjustment

**Improvements:**
- 🔧 Enhanced state management for feeds
- 🔧 Better error handling and validation
- 📝 Comprehensive documentation

**Bug Fixes:**
- 🐛 Fixed timing issues in scheduled publishing

---

**Quick Links:**
- [Getting Started Guide](docs/MockMarketDataFeed.md)
- [Admin Commands](docs/AdminCommands.md)
- [Configuration](docs/Configuration.md)
- [Examples](docs/Examples.md)
