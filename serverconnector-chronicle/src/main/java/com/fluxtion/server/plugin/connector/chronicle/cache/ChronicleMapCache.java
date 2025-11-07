/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.connector.chronicle.cache;

import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.runtime.lifecycle.Lifecycle;
import com.fluxtion.server.plugin.cache.Cache;
import com.fluxtion.server.service.admin.AdminCommandRegistry;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import net.openhft.chronicle.map.ChronicleMap;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * Chronicle Map implementation of the Cache interface.
 *
 * <p>This implementation provides a persistent, off-heap cache backed by Chronicle Map.
 * Data is stored on disk and survives process restarts. The cache supports pluggable
 * serialization through the {@link ValueSerializer} interface.
 *
 * <p><b>Features:</b>
 * <ul>
 *   <li>Off-heap storage - doesn't affect Java heap</li>
 *   <li>Persistent - data survives restarts</li>
 *   <li>High performance - direct memory access</li>
 *   <li>Configurable - entry count, key/value sizes</li>
 *   <li>Pluggable serialization - custom value serializers</li>
 * </ul>
 *
 * <p><b>Configuration:</b>
 * <ul>
 *   <li>{@link #filePath} - Path to Chronicle Map file (required)</li>
 *   <li>{@link #maxEntries} - Maximum number of entries (default: 10,000)</li>
 *   <li>{@link #averageKeySize} - Average key length (default: 32)</li>
 *   <li>{@link #averageValueSize} - Average serialized value size (default: 256)</li>
 *   <li>{@link #valueSerializer} - Custom serializer (default: JavaSerializer)</li>
 * </ul>
 *
 * <p><b>Example Usage:</b>
 * <pre>{@code
 * ChronicleMapCache cache = new ChronicleMapCache();
 * cache.setFilePath("/tmp/my-cache");
 * cache.setMaxEntries(100_000);
 * cache.setAverageValueSize(512);
 * cache.init();
 *
 * cache.put("key1", myObject);
 * MyObject obj = cache.get("key1");
 * }</pre>
 *
 * @see Cache
 * @see ValueSerializer
 * @see JavaSerializer
 */
@Log4j2
public class ChronicleMapCache implements Cache, Lifecycle {

    /**
     * Name of the cache for admin command registration.
     * Default: "chronicle"
     * Admin commands will be registered as: cache.{cacheName}.{command}
     */
    @Getter
    @Setter
    private String cacheName = "chronicle";

    /**
     * Path to the Chronicle Map file. This is where data will be persisted.
     * Must be set before calling {@link #init()}.
     */
    @Getter
    @Setter
    private String filePath;

    /**
     * Maximum number of entries the cache can hold.
     * Default: 10,000 entries.
     */
    @Getter
    @Setter
    private long maxEntries = 10_000;

    /**
     * Average key size in bytes. Used for Chronicle Map sizing calculations.
     * Default: 32 bytes (reasonable for typical string keys).
     */
    @Getter
    @Setter
    private int averageKeySize = 32;

    /**
     * Average value size in bytes after serialization.
     * Default: 256 bytes. Adjust based on your typical object sizes.
     */
    @Getter
    @Setter
    private int averageValueSize = 256;

    /**
     * Value serializer for converting objects to/from byte arrays.
     * Default: {@link JavaSerializer}.
     */
    @Getter
    @Setter
    private ValueSerializer valueSerializer = new JavaSerializer();

    /**
     * The underlying Chronicle Map instance.
     */
    private ChronicleMap<String, byte[]> chronicleMap;

    /**
     * Initialize the Chronicle Map cache.
     * Must be called after configuration properties are set.
     *
     * @throws IllegalStateException if filePath is not set
     * @throws RuntimeException if Chronicle Map creation fails
     */
    @Override
    public void init() {
        if (filePath == null || filePath.isEmpty()) {
            throw new IllegalStateException("filePath must be set before initialization");
        }

        try {
            File mapFile = new File(filePath);
            File parentDir = mapFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    log.warn("Failed to create parent directory: {}", parentDir.getAbsolutePath());
                }
            }

            chronicleMap = ChronicleMap
                    .of(String.class, byte[].class)
                    .name("cache-" + mapFile.getName())
                    .entries(maxEntries)
                    .averageKeySize(averageKeySize)
                    .averageValueSize(averageValueSize)
                    .createPersistedTo(mapFile);

            log.info("ChronicleMapCache initialized at: {} with maxEntries: {}, avgKeySize: {}, avgValueSize: {}",
                    filePath, maxEntries, averageKeySize, averageValueSize);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create Chronicle Map at: " + filePath, e);
        }
    }

    /**
     * Register admin commands for cache management.
     * Commands are registered as: cache.{cacheName}.{command}
     *
     * @param registry the admin command registry
     */
    @ServiceRegistered
    public void registerAdminCommands(AdminCommandRegistry registry) {
        log.info("Registering admin commands for cache: {}", cacheName);
        String prefix = "cache." + cacheName;

        registry.registerCommand(prefix + ".get", this::adminGet);
        registry.registerCommand(prefix + ".keys", this::adminKeys);
        registry.registerCommand(prefix + ".size", this::adminSize);
        registry.registerCommand(prefix + ".remove", this::adminRemove);
        registry.registerCommand(prefix + ".clear", this::adminClear);
        registry.registerCommand(prefix + ".stats", this::adminStats);
        registry.registerCommand(prefix + ".containsKey", this::adminContainsKey);
        registry.registerCommand(prefix + ".put", this::adminPut);
    }

    // ========== Admin Command Implementations ==========

    /**
     * Admin command: Get a value by key.
     * Usage: cache.{cacheName}.get <key>
     */
    private void adminGet(List<String> args, Consumer<String> out, Consumer<String> err) {
        if (args.size() < 2) {
            err.accept("Usage: cache." + cacheName + ".get <key>");
            return;
        }

        try {
            String key = args.get(1);
            Object value = get(key);
            if (value != null) {
                out.accept(key + " -> " + value);
            } else {
                out.accept("Key not found: " + key);
            }
        } catch (Exception e) {
            err.accept("Error retrieving key: " + e.getMessage());
            log.error("Admin get failed", e);
        }
    }

    /**
     * Admin command: List all keys.
     * Usage: cache.{cacheName}.keys
     */
    private void adminKeys(List<String> args, Consumer<String> out, Consumer<String> err) {
        try {
            Collection<String> keySet = keys();
            if (keySet.isEmpty()) {
                out.accept("Cache is empty");
            } else {
                out.accept("Keys (" + keySet.size() + "):\n" + String.join("\n", keySet));
            }
        } catch (Exception e) {
            err.accept("Error listing keys: " + e.getMessage());
            log.error("Admin keys failed", e);
        }
    }

    /**
     * Admin command: Show cache size.
     * Usage: cache.{cacheName}.size
     */
    private void adminSize(List<String> args, Consumer<String> out, Consumer<String> err) {
        try {
            out.accept("Cache size: " + size() + " entries");
        } catch (Exception e) {
            err.accept("Error getting size: " + e.getMessage());
            log.error("Admin size failed", e);
        }
    }

    /**
     * Admin command: Remove a key.
     * Usage: cache.{cacheName}.remove <key>
     */
    private void adminRemove(List<String> args, Consumer<String> out, Consumer<String> err) {
        if (args.size() < 2) {
            err.accept("Usage: cache." + cacheName + ".remove <key>");
            return;
        }

        try {
            String key = args.get(1);
            boolean existed = containsKey(key);
            remove(key);
            if (existed) {
                out.accept("Removed key: " + key);
            } else {
                out.accept("Key did not exist: " + key);
            }
        } catch (Exception e) {
            err.accept("Error removing key: " + e.getMessage());
            log.error("Admin remove failed", e);
        }
    }

    /**
     * Admin command: Clear all entries.
     * Usage: cache.{cacheName}.clear
     */
    private void adminClear(List<String> args, Consumer<String> out, Consumer<String> err) {
        try {
            long sizeBefore = size();
            clear();
            out.accept("Cleared " + sizeBefore + " entries");
        } catch (Exception e) {
            err.accept("Error clearing cache: " + e.getMessage());
            log.error("Admin clear failed", e);
        }
    }

    /**
     * Admin command: Show cache statistics and configuration.
     * Usage: cache.{cacheName}.stats
     */
    private void adminStats(List<String> args, Consumer<String> out, Consumer<String> err) {
        try {
            StringBuilder stats = new StringBuilder();
            stats.append("Chronicle Map Cache Statistics\n");
            stats.append("=".repeat(60)).append("\n");
            stats.append("Cache Name: ").append(cacheName).append("\n");
            stats.append("File Path: ").append(filePath).append("\n");
            stats.append("Current Size: ").append(size()).append(" entries\n");
            stats.append("Max Entries: ").append(maxEntries).append("\n");
            stats.append("Average Key Size: ").append(averageKeySize).append(" bytes\n");
            stats.append("Average Value Size: ").append(averageValueSize).append(" bytes\n");
            stats.append("Serializer: ").append(valueSerializer.getClass().getSimpleName()).append("\n");
            stats.append("Usage: ").append(String.format("%.1f%%", (size() * 100.0 / maxEntries))).append("\n");
            stats.append("=".repeat(60));

            out.accept(stats.toString());
        } catch (Exception e) {
            err.accept("Error getting stats: " + e.getMessage());
            log.error("Admin stats failed", e);
        }
    }

    /**
     * Admin command: Check if a key exists.
     * Usage: cache.{cacheName}.containsKey <key>
     */
    private void adminContainsKey(List<String> args, Consumer<String> out, Consumer<String> err) {
        if (args.size() < 2) {
            err.accept("Usage: cache." + cacheName + ".containsKey <key>");
            return;
        }

        try {
            String key = args.get(1);
            boolean exists = containsKey(key);
            out.accept("Key '" + key + "' exists: " + exists);
        } catch (Exception e) {
            err.accept("Error checking key: " + e.getMessage());
            log.error("Admin containsKey failed", e);
        }
    }

    /**
     * Admin command: Put a string value (for testing).
     * Usage: cache.{cacheName}.put <key> <value>
     */
    private void adminPut(List<String> args, Consumer<String> out, Consumer<String> err) {
        if (args.size() < 3) {
            err.accept("Usage: cache." + cacheName + ".put <key> <value>");
            return;
        }

        try {
            String key = args.get(1);
            String value = String.join(" ", args.subList(2, args.size()));
            put(key, value);
            out.accept("Stored: " + key + " -> " + value);
        } catch (Exception e) {
            err.accept("Error storing value: " + e.getMessage());
            log.error("Admin put failed", e);
        }
    }

    // ========== Cache Interface Implementation ==========

    /**
     * Get all keys in the cache.
     *
     * @return collection of all keys
     */
    @Override
    public Collection<String> keys() {
        checkInitialized();
        return chronicleMap.keySet();
    }

    /**
     * Store a value in the cache.
     *
     * @param key the cache key
     * @param value the value to store (null values are handled by removing the key)
     * @throws IllegalArgumentException if key is null
     * @throws ValueSerializer.SerializationException if serialization fails
     */
    @Override
    public void put(String key, Object value) {
        checkInitialized();
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }

        // Chronicle Map doesn't support null values, so we remove the key instead
        if (value == null) {
            chronicleMap.remove(key);
            log.trace("Removed key for null value: {}", key);
            return;
        }

        byte[] serializedValue = valueSerializer.serialize(value);
        chronicleMap.put(key, serializedValue);
        log.trace("Put key: {}, value type: {}", key, value.getClass().getSimpleName());
    }

    /**
     * Retrieve a value from the cache.
     *
     * @param <T> the expected type
     * @param key the cache key
     * @return the cached value, or null if not found
     * @throws ValueSerializer.SerializationException if deserialization fails
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        checkInitialized();
        if (key == null) {
            return null;
        }

        byte[] serializedValue = chronicleMap.get(key);
        if (serializedValue == null) {
            log.trace("Cache miss for key: {}", key);
            return null;
        }

        T value = valueSerializer.deserialize(serializedValue);
        log.trace("Cache hit for key: {}, value type: {}", key, value != null ? value.getClass().getSimpleName() : "null");
        return value;
    }

    /**
     * Remove a value from the cache.
     *
     * @param key the cache key
     */
    @Override
    public void remove(String key) {
        checkInitialized();
        if (key != null) {
            chronicleMap.remove(key);
            log.trace("Removed key: {}", key);
        }
    }

    /**
     * Close the Chronicle Map and release resources.
     * The data remains persisted on disk.
     */
    @Override
    public void tearDown() {
        if (chronicleMap != null) {
            chronicleMap.close();
            log.info("ChronicleMapCache closed: {}", filePath);
        }
    }

    /**
     * Get the current number of entries in the cache.
     *
     * @return number of entries
     */
    public long size() {
        checkInitialized();
        return chronicleMap.size();
    }

    /**
     * Clear all entries from the cache.
     */
    public void clear() {
        checkInitialized();
        chronicleMap.clear();
        log.info("Cache cleared");
    }

    /**
     * Check if the cache contains a key.
     *
     * @param key the key to check
     * @return true if the key exists
     */
    public boolean containsKey(String key) {
        checkInitialized();
        return key != null && chronicleMap.containsKey(key);
    }

    private void checkInitialized() {
        if (chronicleMap == null) {
            throw new IllegalStateException("ChronicleMapCache not initialized. Call init() first.");
        }
    }
}