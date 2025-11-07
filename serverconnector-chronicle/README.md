# Fluxtion Server Connector - Chronicle

Chronicle Map and Chronicle Queue integration for Fluxtion Server.

## Overview

This module provides connectors for Chronicle data structures:
- **Chronicle Queue**: High-performance persisted message queues
- **Chronicle Map**: Off-heap persisted key-value cache

## Components

### Chronicle Queue

- `ChronicleMessageSink`: Sink for writing messages to Chronicle Queue
- `ChronicleEventSource`: Source for reading messages from Chronicle Queue

### Chronicle Map Cache

- `ChronicleMapCache`: Implementation of the Fluxtion Cache interface using Chronicle Map
- `ValueSerializer`: Interface for pluggable object serialization
- `JavaSerializer`: Default Java serialization implementation

## Chronicle Map Cache

### Features

- **Off-heap storage**: Data stored outside Java heap, reducing GC pressure
- **Persistent**: Data survives process restarts
- **High performance**: Direct memory access with minimal overhead
- **Pluggable serialization**: Custom serializers for optimized storage
- **Type-safe**: Generic get method with type inference

### Quick Start

```java
ChronicleMapCache cache = new ChronicleMapCache();
cache.setFilePath("/tmp/my-cache");
cache.setMaxEntries(100_000);
cache.setAverageValueSize(512);
cache.init();

// Store values
cache.put("user:123", userObject);
cache.put("session:abc", sessionData);

// Retrieve values
User user = cache.get("user:123");
Session session = cache.get("session:abc");

// Cleanup
cache.tearDown();
```

### Configuration

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `filePath` | String | *required* | Path to Chronicle Map file |
| `maxEntries` | long | 10,000 | Maximum number of entries |
| `averageKeySize` | int | 32 | Average key length in bytes |
| `averageValueSize` | int | 256 | Average serialized value size |
| `valueSerializer` | ValueSerializer | JavaSerializer | Serialization strategy |

### Configuration Guidelines

#### Sizing Parameters

**averageKeySize**: Set based on your typical key lengths
- Short keys (e.g., IDs): 16-32 bytes
- Medium keys (e.g., email addresses): 32-64 bytes
- Long keys (e.g., composite keys): 64-128 bytes

**averageValueSize**: Set based on your serialized object sizes
- Small objects: 128-256 bytes
- Medium objects: 256-1024 bytes
- Large objects: 1024+ bytes

**maxEntries**: Set based on expected cache size
- Development: 10,000-100,000
- Production: 100,000-10,000,000+

#### Memory Calculation

Approximate memory usage:
```
Memory = (averageKeySize + averageValueSize + overhead) * maxEntries
```

Where overhead is typically 30-50 bytes per entry.

Example:
```
32 bytes key + 256 bytes value + 40 bytes overhead = 328 bytes/entry
1,000,000 entries * 328 bytes = 312 MB
```

### Custom Serialization

Chronicle Map requires serialization to byte arrays. The default `JavaSerializer` uses Java's built-in serialization, which is convenient but not the most efficient.

#### Implementing a Custom Serializer

```java
public class JsonSerializer implements ValueSerializer {
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public byte[] serialize(Object value) {
        try {
            return mapper.writeValueAsBytes(value);
        } catch (JsonProcessingException e) {
            throw new SerializationException("JSON serialization failed", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T deserialize(byte[] bytes) {
        try {
            return (T) mapper.readValue(bytes, Object.class);
        } catch (IOException e) {
            throw new SerializationException("JSON deserialization failed", e);
        }
    }
}
```

#### Using a Custom Serializer

```java
ChronicleMapCache cache = new ChronicleMapCache();
cache.setValueSerializer(new JsonSerializer());
cache.setFilePath("/tmp/my-cache");
cache.init();
```

#### Recommended Serialization Libraries

- **FST**: Fast, compact Java serialization
- **Kryo**: Efficient binary serialization
- **Protocol Buffers**: Language-agnostic, schema-based
- **MessagePack**: Compact binary JSON-like format
- **Jackson (JSON/CBOR)**: Flexible, well-supported

### Persistence

Chronicle Map data is persisted to disk automatically. The cache file can be:

1. **Reused across restarts**:
   ```java
   // First run - creates and populates cache
   cache.setFilePath("/var/cache/my-app.dat");
   cache.init();
   cache.put("key", "value");
   cache.tearDown();

   // Second run - reloads existing data
   cache.setFilePath("/var/cache/my-app.dat");
   cache.init();
   String value = cache.get("key"); // Returns "value"
   ```

2. **Shared between processes** (with caution):
   Chronicle Map supports multi-process access, but requires careful synchronization

3. **Backed up and restored**:
   Simply copy the cache file to backup/restore data

### Admin Commands

The Chronicle Map Cache integrates with Fluxtion's admin command system, allowing runtime inspection and management of cache data. Commands are automatically registered when the cache is used within a Fluxtion server context.

#### Command Registration

Commands are registered with the prefix `cache.{cacheName}.{command}`, where `cacheName` is configurable (default: "chronicle").

```java
ChronicleMapCache cache = new ChronicleMapCache();
cache.setCacheName("myCache");  // Commands will be: cache.myCache.*
cache.setFilePath("/var/cache/my-cache.dat");
cache.init();

// Commands are auto-registered via @ServiceRegistered annotation
// when AdminCommandRegistry service is available
```

#### Available Commands

| Command | Description | Usage |
|---------|-------------|-------|
| `cache.{name}.get` | Retrieve a value by key | `cache.chronicle.get <key>` |
| `cache.{name}.keys` | List all keys in cache | `cache.chronicle.keys` |
| `cache.{name}.size` | Show cache entry count | `cache.chronicle.size` |
| `cache.{name}.remove` | Remove a key | `cache.chronicle.remove <key>` |
| `cache.{name}.clear` | Clear all entries | `cache.chronicle.clear` |
| `cache.{name}.stats` | Show detailed statistics | `cache.chronicle.stats` |
| `cache.{name}.containsKey` | Check if key exists | `cache.chronicle.containsKey <key>` |
| `cache.{name}.put` | Store a string value | `cache.chronicle.put <key> <value>` |

#### Command Examples

**View cache statistics:**
```
> cache.chronicle.stats

Chronicle Map Cache Statistics
============================================================
Cache Name: chronicle
File Path: /var/cache/my-cache.dat
Current Size: 15234 entries
Max Entries: 100000
Average Key Size: 32 bytes
Average Value Size: 256 bytes
Serializer: JavaSerializer
Usage: 15.2%
============================================================
```

**Retrieve a value:**
```
> cache.chronicle.get user:12345
user:12345 -> User{id=12345, name='John Doe', email='john@example.com'}
```

**List all keys:**
```
> cache.chronicle.keys
Keys (3):
user:12345
session:abc-def-ghi
config:app-settings
```

**Check cache size:**
```
> cache.chronicle.size
Cache size: 15234 entries
```

**Check if key exists:**
```
> cache.chronicle.containsKey user:12345
Key 'user:12345' exists: true
```

**Remove a key:**
```
> cache.chronicle.remove user:12345
Removed key: user:12345
```

**Clear cache:**
```
> cache.chronicle.clear
Cleared 15234 entries
```

**Put a test value:**
```
> cache.chronicle.put test-key test value with spaces
Stored: test-key -> test value with spaces
```

#### Integration with Fluxtion Server

When using Chronicle Map Cache with Fluxtion Server, admin commands are accessible through:

1. **Telnet Admin Interface:**
   ```bash
   telnet localhost 2199
   > cache.chronicle.stats
   ```

2. **REST API:**
   ```bash
   curl http://localhost:8001/admin/cache.chronicle.stats
   ```

3. **Programmatic Access:**
   ```java
   AdminCommandRegistry registry = // ... get from server context
   registry.executeCommand("cache.chronicle.get",
       List.of("cache.chronicle.get", "myKey"),
       System.out::println,
       System.err::println);
   ```

### Best Practices

1. **Size Configuration**:
   - Overestimate `averageValueSize` rather than underestimate
   - Chronicle Map can handle larger values, but performance degrades if too small
   - Monitor actual sizes and adjust if needed

2. **Serialization**:
   - Use efficient serializers for production (avoid Java serialization)
   - Ensure serialized objects are compatible across code versions
   - Consider schema evolution for long-lived caches

3. **Resource Management**:
   - Always call `tearDown()` to properly close the cache
   - Use try-with-resources or lifecycle management
   - Don't create multiple cache instances for the same file

4. **Error Handling**:
   - Handle `SerializationException` for problematic objects
   - Validate `filePath` has write permissions
   - Check disk space for large caches

5. **Performance**:
   - Chronicle Map excels with many small/medium objects
   - For very large objects (>10KB), consider alternative storage
   - Batch operations when possible

### Examples

#### Example 1: Session Cache

```java
public class SessionCache {
    private final ChronicleMapCache cache;

    public SessionCache() {
        cache = new ChronicleMapCache();
        cache.setFilePath("/var/cache/sessions.dat");
        cache.setMaxEntries(1_000_000);
        cache.setAverageKeySize(40); // UUID length
        cache.setAverageValueSize(512); // Session data
        cache.init();
    }

    public void storeSession(String sessionId, SessionData data) {
        cache.put(sessionId, data);
    }

    public SessionData getSession(String sessionId) {
        return cache.get(sessionId);
    }

    public void invalidateSession(String sessionId) {
        cache.remove(sessionId);
    }

    public void shutdown() {
        cache.tearDown();
    }
}
```

#### Example 2: Configuration Cache with TTL

```java
public class ConfigCache {
    private final ChronicleMapCache cache;
    private final Map<String, Long> timestamps = new ConcurrentHashMap<>();
    private final long ttlMillis = 3600_000; // 1 hour

    public ConfigCache() {
        cache = new ChronicleMapCache();
        cache.setFilePath("/var/cache/config.dat");
        cache.setMaxEntries(10_000);
        cache.init();
    }

    public <T> T getConfig(String key, Supplier<T> loader) {
        Long timestamp = timestamps.get(key);
        long now = System.currentTimeMillis();

        if (timestamp == null || (now - timestamp) > ttlMillis) {
            T value = loader.get();
            cache.put(key, value);
            timestamps.put(key, now);
            return value;
        }

        return cache.get(key);
    }
}
```

#### Example 3: Market Data Cache

```java
public class MarketDataCache {
    private final ChronicleMapCache cache;

    public MarketDataCache() {
        cache = new ChronicleMapCache();
        cache.setFilePath("/var/cache/market-data.dat");
        cache.setMaxEntries(10_000);
        cache.setAverageKeySize(20); // Symbol length
        cache.setAverageValueSize(2048); // Market data book
        cache.init();
    }

    public void updateMarketData(String symbol, MultilevelMarketDataBook book) {
        cache.put(symbol, book);
    }

    public MultilevelMarketDataBook getMarketData(String symbol) {
        return cache.get(symbol);
    }

    public Collection<String> getTrackedSymbols() {
        return cache.keys();
    }
}
```

## Testing

```bash
mvn test
```

The test suite includes:
- Basic CRUD operations
- Persistence verification
- Custom serializer testing
- Performance/stress tests
- Exception handling

## Performance Characteristics

| Operation | Performance |
|-----------|-------------|
| Put | ~1-2 microseconds |
| Get | ~0.5-1 microseconds |
| Remove | ~1-2 microseconds |
| Persistence | Automatic, no overhead |

**Note**: Performance varies based on:
- Hardware (SSD vs HDD)
- Object size
- Serialization method
- JVM configuration

## Requirements

- Java 21+
- Chronicle Map 3.x (managed via BOM)
- Chronicle Queue 5.x (managed via BOM)

## Dependencies

```xml
<dependency>
    <groupId>com.fluxtion</groupId>
    <artifactId>serverconnector-chronicle</artifactId>
    <version>0.2.11-SNAPSHOT</version>
</dependency>
```

## License

Licensed under the AGPL-3.0-only license.

## Further Reading

- [Chronicle Map Documentation](https://github.com/OpenHFT/Chronicle-Map)
- [Chronicle Queue Documentation](https://github.com/OpenHFT/Chronicle-Queue)
- [Fluxtion Server Cache Interface](../serverplugin-cache)
