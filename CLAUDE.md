# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Fluxtion Server Plugins is a collection of plugins and libraries for the Fluxtion server framework. The project provides connectors, server plugins, and trading libraries for building event-driven applications using the Fluxtion event processing framework.

**Key Information:**
- Java 21 required
- Maven multi-module project
- License: AGPL-3.0-only
- Uses Lombok for code generation
- Core dependencies: Fluxtion runtime (v9.7.14), Fluxtion server (v0.2.7)

## Build Commands

```bash
# Build entire project
mvn clean install

# Build without tests
mvn clean install -DskipTests

# Run tests
mvn test

# Run tests for specific module
mvn test -pl serverplugin-rest

# Build with release profile (sources, javadocs, GPG signing)
mvn clean install -P release

# Package project
mvn package
```

## Testing

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=FileEventSourceTest

# Run single test method
mvn test -Dtest=FileEventSourceTest#testReadEvents_tailCommitted_withCacheThenPublish

# Run tests with specific Java module options (already configured in parent pom.xml)
# Tests require these JVM arguments:
# --add-opens java.base/java.lang.reflect=ALL-UNNAMED
# --add-opens java.base/sun.nio.ch=ALL-UNNAMED
# --add-opens java.base/java.lang=ALL-UNNAMED
# --add-exports java.base/jdk.internal.ref=ALL-UNNAMED
# --add-exports jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED
```

## Module Structure

The project is organized into three main categories:

### Server Connectors (serverconnector-*)
Event sources and message sinks that connect external systems to Fluxtion:
- **serverconnector-file**: File-based event sources/sinks with tailing and read strategies
- **serverconnector-multicast**: UDP multicast messaging
- **serverconnector-chronicle**: Chronicle Queue integration
- **serverconnector-aeron**: Aeron messaging with archive support

### Server Plugins (serverplugin-*)
Service integrations and infrastructure components:
- **serverplugin-rest**: REST API support via Javalin web server
- **serverplugin-kafka**: Kafka consumer/producer integration
- **serverplugin-jdbc**: JDBC connection management
- **serverplugin-cache**: JSON file caching
- **serverplugin-admintelnet**: Telnet admin console
- **serverplugin-javabuilder**: Java event handler builder
- **serverplugin-springbuilder**: Spring-based event handler builder
- **serverplugin-trading**: Trading components including QuickFIX/J integration and mock venues

### Server Libraries (serverlibrary-*)
Reusable trading and serialization libraries:
- **serverlibrary-trading-api**: Core trading API defining service interfaces (MarketDataFeed, OrderExecutor, TradeServiceListener)
- **serverlibrary-pnl**: P&L calculation utilities
- **serverlibrary-jsonserialiser**: JSON serialization utilities

## Architecture Patterns

### Event Source Pattern
Event sources extend `AbstractAgentHostedEventSourceService` and implement lifecycle methods:
- `start()`: Initialize resources and connections
- `doWork()`: Poll/read events and publish to queue
- `stop()`: Clean up resources
- `startComplete()`: Signal ready state after initialization

Example: `FileEventSource` reads files with configurable read strategies (COMMITTED, EARLIEST, LATEST, ONCE_EARLIEST, ONCE_LATEST).

### Message Sink Pattern
Message sinks write events to external systems. They handle serialization and delivery guarantees.

### Service Discovery and Lifecycle
Trading components implement `TradeServiceListener` for:
- Lifecycle hooks: `init()`, `start()`, `stop()`
- Service discovery: `marketFeedRegistered()`, `orderExecutorRegistered()`
- Event processing: `onEvent()`, `calculate()`, `postCalculate()`
- Admin integration: `adminClient()`

### Builder Pattern
Event handlers can be constructed using builders:
- `SpringEventHandlerBuilder`: Loads Fluxtion graph from Spring XML with optional audit configuration
- Builders implement `Supplier<T extends EventProcessor<?>>` for lazy initialization

## Maven Release Process

The project uses maven-release-plugin with specific configuration:
- Version tags formatted as `v@{project.version}` (e.g., v0.2.9)
- Auto-version submodules enabled
- Release profile includes: sources, javadocs, GPG signing, Maven Central publishing

```bash
# Prepare and perform release (done via CI on release branch)
mvn -P release release:prepare release:perform
```

## Key Dependencies

- **Fluxtion runtime/compiler**: Core event processing framework
- **Lombok**: Code generation (provided scope)
- **Log4j2**: Logging framework
- **JUnit 5**: Testing framework
- **QuickFIX/J**: FIX protocol implementation (trading plugin)
- **Javalin**: Lightweight web framework (REST plugin)
- **Agrona**: High-performance data structures

## Working with Trading Components

The trading API defines key abstractions:
- `MarketDataFeed`: Provides market data (quotes, trades, order books)
- `OrderExecutor`: Handles order submission and lifecycle management
- `TradeServiceListener`: Base interface for trading nodes to receive lifecycle events and service references

Trading plugins provide:
- QuickFIX/J integration for FIX protocol connectivity
- Mock venues for testing trading strategies
- Market data book generators for simulation

## Module Dependencies

When adding dependencies between modules:
- Use `${project.version}` for inter-module dependencies
- Fluxtion server is in `provided` scope (supplied by runtime)
- Fluxtion compiler is in `provided` scope (only needed for build-time generation)

## Common Development Tasks

### Adding a New Connector
1. Create module with naming: `serverconnector-<name>`
2. Extend `AbstractAgentHostedEventSourceService` for sources
3. Implement lifecycle methods and `doWork()` for event polling
4. Add module to parent pom.xml `<modules>` section
5. Add tests using JUnit 5 with `@TempDir` for file-based testing

### Adding a New Plugin
1. Create module with naming: `serverplugin-<name>`
2. Implement service interface or extend appropriate base class
3. Consider lifecycle integration points
4. Add module to parent pom.xml `<modules>` section

### Working with Fluxtion Event Processing
- Event processors are built via builders or Spring configuration
- Nodes in the graph receive events based on type matching
- Use `@OnEventHandler` for event handling methods
- Event auditing can be enabled for debugging (configurable trace level)