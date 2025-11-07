/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.connector.chronicle.cache;

import org.junit.jupiter.api.*;

import java.io.File;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class ChronicleMapCacheTest {

    private ChronicleMapCache cache;
    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("chronicle-cache-test");
        cache = new ChronicleMapCache();
        cache.setFilePath(tempDir.resolve("test-cache").toString());
        cache.setMaxEntries(1000);
        cache.setAverageKeySize(32);
        cache.setAverageValueSize(256);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (cache != null) {
            cache.tearDown();
        }
        // Clean up temp directory
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    // ========== Initialization Tests ==========

    @Test
    @DisplayName("Test initialization without file path throws exception")
    void testInitWithoutFilePath() {
        ChronicleMapCache uninitializedCache = new ChronicleMapCache();
        assertThrows(IllegalStateException.class, uninitializedCache::init);
    }

    @Test
    @DisplayName("Test successful initialization")
    void testSuccessfulInit() {
        assertDoesNotThrow(() -> cache.init());
        assertEquals(0, cache.size());
    }

    @Test
    @DisplayName("Test operations before initialization throw exception")
    void testOperationsBeforeInit() {
        assertThrows(IllegalStateException.class, () -> cache.put("key", "value"));
        assertThrows(IllegalStateException.class, () -> cache.get("key"));
        assertThrows(IllegalStateException.class, cache::keys);
        assertThrows(IllegalStateException.class, () -> cache.remove("key"));
    }

    // ========== Basic Operations Tests ==========

    @Test
    @DisplayName("Test put and get with string value")
    void testPutAndGetString() {
        cache.init();
        cache.put("key1", "value1");
        String result = cache.get("key1");
        assertEquals("value1", result);
    }

    @Test
    @DisplayName("Test put and get with integer value")
    void testPutAndGetInteger() {
        cache.init();
        cache.put("intKey", 42);
        Integer result = cache.get("intKey");
        assertEquals(42, result);
    }

    @Test
    @DisplayName("Test put and get with custom object")
    void testPutAndGetCustomObject() {
        cache.init();
        TestObject obj = new TestObject("test", 123);
        cache.put("objKey", obj);

        TestObject result = cache.get("objKey");
        assertNotNull(result);
        assertEquals("test", result.getName());
        assertEquals(123, result.getValue());
    }

    @Test
    @DisplayName("Test put with null key throws exception")
    void testPutNullKey() {
        cache.init();
        assertThrows(IllegalArgumentException.class, () -> cache.put(null, "value"));
    }

    @Test
    @DisplayName("Test put with null value removes the key")
    void testPutNullValue() {
        cache.init();
        // First put a value
        cache.put("nullKey", "someValue");
        assertEquals("someValue", cache.get("nullKey"));

        // Putting null should remove the key
        cache.put("nullKey", null);
        assertNull(cache.get("nullKey"));
        assertFalse(cache.containsKey("nullKey"));
    }

    @Test
    @DisplayName("Test get with null key returns null")
    void testGetNullKey() {
        cache.init();
        assertNull(cache.get(null));
    }

    @Test
    @DisplayName("Test get non-existent key returns null")
    void testGetNonExistentKey() {
        cache.init();
        assertNull(cache.get("nonExistent"));
    }

    @Test
    @DisplayName("Test getOrDefault with existing key")
    void testGetOrDefaultExisting() {
        cache.init();
        cache.put("key1", "value1");
        String result = cache.getOrDefault("key1", "default");
        assertEquals("value1", result);
    }

    @Test
    @DisplayName("Test getOrDefault with non-existent key")
    void testGetOrDefaultNonExistent() {
        cache.init();
        String result = cache.getOrDefault("nonExistent", "default");
        assertEquals("default", result);
    }

    @Test
    @DisplayName("Test update existing key")
    void testUpdateExistingKey() {
        cache.init();
        cache.put("key1", "value1");
        cache.put("key1", "value2");
        assertEquals("value2", cache.get("key1"));
    }

    @Test
    @DisplayName("Test remove existing key")
    void testRemoveExistingKey() {
        cache.init();
        cache.put("key1", "value1");
        cache.remove("key1");
        assertNull(cache.get("key1"));
    }

    @Test
    @DisplayName("Test remove non-existent key")
    void testRemoveNonExistentKey() {
        cache.init();
        assertDoesNotThrow(() -> cache.remove("nonExistent"));
    }

    @Test
    @DisplayName("Test remove with null key")
    void testRemoveNullKey() {
        cache.init();
        assertDoesNotThrow(() -> cache.remove(null));
    }

    // ========== Collection Operations Tests ==========

    @Test
    @DisplayName("Test keys returns empty collection when empty")
    void testKeysEmpty() {
        cache.init();
        Collection<String> keys = cache.keys();
        assertNotNull(keys);
        assertTrue(keys.isEmpty());
    }

    @Test
    @DisplayName("Test keys returns all keys")
    void testKeysWithData() {
        cache.init();
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        cache.put("key3", "value3");

        Collection<String> keys = cache.keys();
        assertEquals(3, keys.size());
        assertTrue(keys.contains("key1"));
        assertTrue(keys.contains("key2"));
        assertTrue(keys.contains("key3"));
    }

    @Test
    @DisplayName("Test size returns correct count")
    void testSize() {
        cache.init();
        assertEquals(0, cache.size());

        cache.put("key1", "value1");
        assertEquals(1, cache.size());

        cache.put("key2", "value2");
        assertEquals(2, cache.size());

        cache.remove("key1");
        assertEquals(1, cache.size());
    }

    @Test
    @DisplayName("Test containsKey")
    void testContainsKey() {
        cache.init();
        cache.put("key1", "value1");

        assertTrue(cache.containsKey("key1"));
        assertFalse(cache.containsKey("nonExistent"));
        assertFalse(cache.containsKey(null));
    }

    @Test
    @DisplayName("Test clear removes all entries")
    void testClear() {
        cache.init();
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        cache.put("key3", "value3");

        assertEquals(3, cache.size());
        cache.clear();
        assertEquals(0, cache.size());
        assertNull(cache.get("key1"));
    }

    // ========== Persistence Tests ==========

    @Test
    @DisplayName("Test data persists across cache instances")
    void testPersistence() {
        String cachePath = tempDir.resolve("persistent-cache").toString();

        // First instance - write data
        ChronicleMapCache cache1 = new ChronicleMapCache();
        cache1.setFilePath(cachePath);
        cache1.init();
        cache1.put("persistKey", "persistValue");
        cache1.tearDown();

        // Second instance - read data
        ChronicleMapCache cache2 = new ChronicleMapCache();
        cache2.setFilePath(cachePath);
        cache2.init();
        String result = cache2.get("persistKey");
        assertEquals("persistValue", result);
        cache2.tearDown();
    }

    @Test
    @DisplayName("Test persistence with complex objects")
    void testPersistenceComplexObjects() {
        String cachePath = tempDir.resolve("complex-cache").toString();

        // First instance
        ChronicleMapCache cache1 = new ChronicleMapCache();
        cache1.setFilePath(cachePath);
        cache1.init();
        TestObject obj = new TestObject("persistent", 999);
        cache1.put("complexKey", obj);
        cache1.tearDown();

        // Second instance
        ChronicleMapCache cache2 = new ChronicleMapCache();
        cache2.setFilePath(cachePath);
        cache2.init();
        TestObject result = cache2.get("complexKey");
        assertNotNull(result);
        assertEquals("persistent", result.getName());
        assertEquals(999, result.getValue());
        cache2.tearDown();
    }

    // ========== Custom Serializer Tests ==========

    @Test
    @DisplayName("Test with custom serializer")
    void testCustomSerializer() {
        cache.setValueSerializer(new TestSerializer());
        cache.init();

        cache.put("customKey", "TEST");
        String result = cache.get("customKey");
        // TestSerializer converts to uppercase
        assertEquals("TEST-SERIALIZED", result);
    }

    @Test
    @DisplayName("Test serialization exception handling")
    void testSerializationException() {
        cache.setValueSerializer(new FailingSerializer());
        cache.init();

        assertThrows(ValueSerializer.SerializationException.class, () -> cache.put("key", "value"));
    }

    // ========== Performance/Stress Tests ==========

    @Test
    @DisplayName("Test with multiple entries")
    void testMultipleEntries() {
        cache.init();

        // Add 100 entries
        for (int i = 0; i < 100; i++) {
            cache.put("key" + i, "value" + i);
        }

        assertEquals(100, cache.size());

        // Verify all entries
        for (int i = 0; i < 100; i++) {
            assertEquals("value" + i, cache.get("key" + i));
        }
    }

    @Test
    @DisplayName("Test with large values")
    void testLargeValues() {
        cache.setAverageValueSize(10000);
        cache.init();

        StringBuilder largeString = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            largeString.append("This is a test string that will be repeated many times. ");
        }

        cache.put("largeKey", largeString.toString());
        String result = cache.get("largeKey");
        assertEquals(largeString.toString(), result);
    }

    // ========== Admin Command Tests ==========

    @Test
    @DisplayName("Test admin command registration")
    void testAdminCommandRegistration() {
        cache.init();
        List<String> registeredCommands = new ArrayList<>();

        TestAdminRegistry registry = new TestAdminRegistry(registeredCommands);
        cache.registerAdminCommands(registry);

        assertEquals(8, registeredCommands.size());
        assertTrue(registeredCommands.contains("cache.chronicle.get"));
        assertTrue(registeredCommands.contains("cache.chronicle.keys"));
        assertTrue(registeredCommands.contains("cache.chronicle.size"));
        assertTrue(registeredCommands.contains("cache.chronicle.remove"));
        assertTrue(registeredCommands.contains("cache.chronicle.clear"));
        assertTrue(registeredCommands.contains("cache.chronicle.stats"));
        assertTrue(registeredCommands.contains("cache.chronicle.containsKey"));
        assertTrue(registeredCommands.contains("cache.chronicle.put"));
    }

    @Test
    @DisplayName("Test admin get command")
    void testAdminGetCommand() {
        cache.init();
        cache.put("testKey", "testValue");

        List<String> outputMessages = new ArrayList<>();
        List<String> errorMessages = new ArrayList<>();

        TestAdminRegistry registry = new TestAdminRegistry(outputMessages, errorMessages);
        cache.registerAdminCommands(registry);

        // Test successful get
        registry.executeCommand("cache.chronicle.get", Arrays.asList("get", "testKey"));
        assertEquals(1, outputMessages.size());
        assertTrue(outputMessages.get(0).contains("testKey -> testValue"));

        // Test non-existent key
        outputMessages.clear();
        registry.executeCommand("cache.chronicle.get", Arrays.asList("get", "nonExistent"));
        assertTrue(outputMessages.get(0).contains("Key not found"));

        // Test missing argument
        outputMessages.clear();
        registry.executeCommand("cache.chronicle.get", Arrays.asList("get"));
        assertEquals(1, errorMessages.size());
        assertTrue(errorMessages.get(0).contains("Usage"));
    }

    @Test
    @DisplayName("Test admin keys command")
    void testAdminKeysCommand() {
        cache.init();
        cache.put("key1", "value1");
        cache.put("key2", "value2");

        List<String> outputMessages = new ArrayList<>();
        List<String> errorMessages = new ArrayList<>();

        TestAdminRegistry registry = new TestAdminRegistry(outputMessages, errorMessages);
        cache.registerAdminCommands(registry);

        registry.executeCommand("cache.chronicle.keys", Arrays.asList("keys"));
        assertEquals(1, outputMessages.size());
        assertTrue(outputMessages.get(0).contains("Keys (2)"));
        assertTrue(outputMessages.get(0).contains("key1"));
        assertTrue(outputMessages.get(0).contains("key2"));
    }

    @Test
    @DisplayName("Test admin size command")
    void testAdminSizeCommand() {
        cache.init();
        cache.put("key1", "value1");
        cache.put("key2", "value2");

        List<String> outputMessages = new ArrayList<>();
        List<String> errorMessages = new ArrayList<>();

        TestAdminRegistry registry = new TestAdminRegistry(outputMessages, errorMessages);
        cache.registerAdminCommands(registry);

        registry.executeCommand("cache.chronicle.size", Arrays.asList("size"));
        assertEquals(1, outputMessages.size());
        assertTrue(outputMessages.get(0).contains("2 entries"));
    }

    @Test
    @DisplayName("Test admin remove command")
    void testAdminRemoveCommand() {
        cache.init();
        cache.put("testKey", "testValue");

        List<String> outputMessages = new ArrayList<>();
        List<String> errorMessages = new ArrayList<>();

        TestAdminRegistry registry = new TestAdminRegistry(outputMessages, errorMessages);
        cache.registerAdminCommands(registry);

        // Test successful remove
        registry.executeCommand("cache.chronicle.remove", Arrays.asList("remove", "testKey"));
        assertTrue(outputMessages.get(0).contains("Removed key: testKey"));
        assertNull(cache.get("testKey"));

        // Test missing argument
        outputMessages.clear();
        registry.executeCommand("cache.chronicle.remove", Arrays.asList("remove"));
        assertEquals(1, errorMessages.size());
        assertTrue(errorMessages.get(0).contains("Usage"));
    }

    @Test
    @DisplayName("Test admin clear command")
    void testAdminClearCommand() {
        cache.init();
        cache.put("key1", "value1");
        cache.put("key2", "value2");

        List<String> outputMessages = new ArrayList<>();
        List<String> errorMessages = new ArrayList<>();

        TestAdminRegistry registry = new TestAdminRegistry(outputMessages, errorMessages);
        cache.registerAdminCommands(registry);

        registry.executeCommand("cache.chronicle.clear", Arrays.asList("clear"));
        assertTrue(outputMessages.get(0).contains("Cleared 2 entries"));
        assertEquals(0, cache.size());
    }

    @Test
    @DisplayName("Test admin stats command")
    void testAdminStatsCommand() {
        cache.init();
        cache.put("key1", "value1");

        List<String> outputMessages = new ArrayList<>();
        List<String> errorMessages = new ArrayList<>();

        TestAdminRegistry registry = new TestAdminRegistry(outputMessages, errorMessages);
        cache.registerAdminCommands(registry);

        registry.executeCommand("cache.chronicle.stats", Arrays.asList("stats"));
        String stats = outputMessages.get(0);
        assertTrue(stats.contains("Cache Name: chronicle"));
        assertTrue(stats.contains("Current Size: 1 entries"));
        assertTrue(stats.contains("Max Entries: 1000"));
        assertTrue(stats.contains("JavaSerializer"));
    }

    @Test
    @DisplayName("Test admin containsKey command")
    void testAdminContainsKeyCommand() {
        cache.init();
        cache.put("existingKey", "value");

        List<String> outputMessages = new ArrayList<>();
        List<String> errorMessages = new ArrayList<>();

        TestAdminRegistry registry = new TestAdminRegistry(outputMessages, errorMessages);
        cache.registerAdminCommands(registry);

        // Test existing key
        registry.executeCommand("cache.chronicle.containsKey", Arrays.asList("containsKey", "existingKey"));
        assertTrue(outputMessages.get(0).contains("exists: true"));

        // Test non-existing key
        outputMessages.clear();
        registry.executeCommand("cache.chronicle.containsKey", Arrays.asList("containsKey", "nonExistent"));
        assertTrue(outputMessages.get(0).contains("exists: false"));

        // Test missing argument
        outputMessages.clear();
        registry.executeCommand("cache.chronicle.containsKey", Arrays.asList("containsKey"));
        assertEquals(1, errorMessages.size());
        assertTrue(errorMessages.get(0).contains("Usage"));
    }

    @Test
    @DisplayName("Test admin put command")
    void testAdminPutCommand() {
        cache.init();

        List<String> outputMessages = new ArrayList<>();
        List<String> errorMessages = new ArrayList<>();

        TestAdminRegistry registry = new TestAdminRegistry(outputMessages, errorMessages);
        cache.registerAdminCommands(registry);

        // Test successful put
        registry.executeCommand("cache.chronicle.put", Arrays.asList("put", "testKey", "test", "value"));
        assertTrue(outputMessages.get(0).contains("Stored: testKey -> test value"));
        assertEquals("test value", cache.get("testKey"));

        // Test missing arguments
        outputMessages.clear();
        registry.executeCommand("cache.chronicle.put", Arrays.asList("put", "key"));
        assertEquals(1, errorMessages.size());
        assertTrue(errorMessages.get(0).contains("Usage"));
    }

    @Test
    @DisplayName("Test custom cache name in admin commands")
    void testCustomCacheName() {
        cache.setCacheName("myCache");
        cache.init();

        List<String> registeredCommands = new ArrayList<>();
        TestAdminRegistry registry = new TestAdminRegistry(registeredCommands);
        cache.registerAdminCommands(registry);

        assertTrue(registeredCommands.contains("cache.myCache.get"));
        assertTrue(registeredCommands.contains("cache.myCache.keys"));
        assertTrue(registeredCommands.contains("cache.myCache.size"));
    }

    // ========== Helper Classes ==========

    /**
     * Test admin registry for capturing command registrations and execution.
     */
    static class TestAdminRegistry implements com.fluxtion.server.service.admin.AdminCommandRegistry {
        private final List<String> registeredCommands;
        private final Map<String, com.fluxtion.server.service.admin.AdminFunction<?, ?>> commands = new HashMap<>();
        private final Consumer<String> outConsumer;
        private final Consumer<String> errConsumer;

        public TestAdminRegistry(List<String> registeredCommands) {
            this.registeredCommands = registeredCommands;
            this.outConsumer = s -> {};
            this.errConsumer = s -> {};
        }

        public TestAdminRegistry(List<String> outputMessages, List<String> errorMessages) {
            this.registeredCommands = new ArrayList<>();
            this.outConsumer = outputMessages::add;
            this.errConsumer = errorMessages::add;
        }

        @Override
        public <OUT, ERR> void registerCommand(String name, com.fluxtion.server.service.admin.AdminFunction<OUT, ERR> command) {
            registeredCommands.add(name);
            commands.put(name, command);
        }

        @Override
        public void processAdminCommandRequest(com.fluxtion.server.service.admin.AdminCommandRequest request) {
        }

        @Override
        public List<String> commandList() {
            return new ArrayList<>(registeredCommands);
        }

        @SuppressWarnings("unchecked")
        public void executeCommand(String name, List<String> args) {
            com.fluxtion.server.service.admin.AdminFunction<Object, Object> cmd =
                    (com.fluxtion.server.service.admin.AdminFunction<Object, Object>) commands.get(name);
            if (cmd != null) {
                cmd.processAdminCommand(args, (Consumer<Object>) (Object o) -> outConsumer.accept(o.toString()),
                        (Consumer<Object>) (Object o) -> errConsumer.accept(o.toString()));
            }
        }
    }

    /**
     * Test object for serialization testing.
     */
    static class TestObject implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String name;
        private final int value;

        public TestObject(String name, int value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public int getValue() {
            return value;
        }
    }

    /**
     * Custom serializer for testing.
     */
    static class TestSerializer implements ValueSerializer {
        @Override
        public byte[] serialize(Object value) {
            if (value == null) return null;
            return (value.toString() + "-SERIALIZED").getBytes();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T deserialize(byte[] bytes) {
            if (bytes == null) return null;
            return (T) new String(bytes);
        }
    }

    /**
     * Failing serializer for exception testing.
     */
    static class FailingSerializer implements ValueSerializer {
        @Override
        public byte[] serialize(Object value) {
            throw new SerializationException("Intentional failure", new RuntimeException());
        }

        @Override
        public <T> T deserialize(byte[] bytes) {
            throw new SerializationException("Intentional failure", new RuntimeException());
        }
    }
}
