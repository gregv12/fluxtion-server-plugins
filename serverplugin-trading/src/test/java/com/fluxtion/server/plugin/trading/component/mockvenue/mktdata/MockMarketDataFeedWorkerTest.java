package com.fluxtion.server.plugin.trading.component.mockvenue.mktdata;

import com.fluxtion.server.service.admin.AdminCommandRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class MockMarketDataFeedWorkerTest {

    private MockMarketDataFeedWorker feedWorker;
    private List<String> outputMessages;
    private List<String> errorMessages;
    private Consumer<String> outConsumer;
    private Consumer<String> errConsumer;

    @BeforeEach
    void setUp() {
        feedWorker = new MockMarketDataFeedWorker();
        outputMessages = new ArrayList<>();
        errorMessages = new ArrayList<>();
        outConsumer = outputMessages::add;
        errConsumer = errorMessages::add;
    }

    // ========== Control Commands Tests ==========

    @Test
    @DisplayName("Test startFeed sets running to true and unpauses")
    void testStartFeed() {
        feedWorker.startFeed(Arrays.asList("start"), outConsumer, errConsumer);

        assertTrue(outputMessages.get(0).contains("started"));
        assertTrue(errorMessages.isEmpty());
    }

    @Test
    @DisplayName("Test stopFeed sets running to false and clears configs")
    void testStopFeed() {
        // Add a config first
        feedWorker.addConfig(Arrays.asList("addConfig", "EURUSD", "1.08", "1.09", "0.0001", "0.0005", "100000", "1000000"),
                            outConsumer, errConsumer);
        outputMessages.clear();

        // Stop the feed
        feedWorker.stopFeed(Arrays.asList("stop"), outConsumer, errConsumer);

        assertTrue(outputMessages.get(0).contains("stopped"));
        assertEquals(0, feedWorker.getMarketDataBookConfigs().size());
    }

    @Test
    @DisplayName("Test pauseFeed when running")
    void testPauseFeedWhenRunning() {
        feedWorker.startFeed(Arrays.asList("start"), outConsumer, errConsumer);
        outputMessages.clear();

        feedWorker.pauseFeed(Arrays.asList("pause"), outConsumer, errConsumer);

        assertTrue(outputMessages.get(0).contains("paused"));
        assertTrue(errorMessages.isEmpty());
    }

    @Test
    @DisplayName("Test pauseFeed when not running returns error")
    void testPauseFeedWhenNotRunning() {
        feedWorker.stopFeed(Arrays.asList("stop"), outConsumer, errConsumer);
        outputMessages.clear();
        errorMessages.clear();

        feedWorker.pauseFeed(Arrays.asList("pause"), outConsumer, errConsumer);

        assertTrue(errorMessages.get(0).contains("not running"));
        assertTrue(outputMessages.isEmpty());
    }

    @Test
    @DisplayName("Test resumeFeed when running and paused")
    void testResumeFeedWhenPaused() {
        feedWorker.startFeed(Arrays.asList("start"), outConsumer, errConsumer);
        feedWorker.pauseFeed(Arrays.asList("pause"), outConsumer, errConsumer);
        outputMessages.clear();

        feedWorker.resumeFeed(Arrays.asList("resume"), outConsumer, errConsumer);

        assertTrue(outputMessages.get(0).contains("resumed"));
        assertTrue(errorMessages.isEmpty());
    }

    @Test
    @DisplayName("Test resumeFeed when not running returns error")
    void testResumeFeedWhenNotRunning() {
        feedWorker.stopFeed(Arrays.asList("stop"), outConsumer, errConsumer);
        outputMessages.clear();
        errorMessages.clear();

        feedWorker.resumeFeed(Arrays.asList("resume"), outConsumer, errConsumer);

        assertTrue(errorMessages.get(0).contains("not running"));
        assertTrue(outputMessages.isEmpty());
    }

    // ========== Configuration Commands Tests ==========

    @Test
    @DisplayName("Test setPublishRate with valid value")
    void testSetPublishRateValid() {
        feedWorker.setPublishRate(Arrays.asList("setPublishRate", "500"), outConsumer, errConsumer);

        assertEquals(500, feedWorker.getPublishRateMillis());
        assertTrue(outputMessages.get(0).contains("500"));
        assertTrue(errorMessages.isEmpty());
    }

    @Test
    @DisplayName("Test setPublishRate with invalid value returns error")
    void testSetPublishRateInvalid() {
        feedWorker.setPublishRate(Arrays.asList("setPublishRate", "0"), outConsumer, errConsumer);

        assertTrue(errorMessages.get(0).contains("positive"));
        assertEquals(1000, feedWorker.getPublishRateMillis()); // Should remain unchanged
    }

    @Test
    @DisplayName("Test setPublishRate with non-numeric value returns error")
    void testSetPublishRateNonNumeric() {
        feedWorker.setPublishRate(Arrays.asList("setPublishRate", "abc"), outConsumer, errConsumer);

        assertTrue(errorMessages.get(0).contains("Invalid number"));
        assertEquals(1000, feedWorker.getPublishRateMillis());
    }

    @Test
    @DisplayName("Test setPublishRate with missing argument returns error")
    void testSetPublishRateMissingArgument() {
        feedWorker.setPublishRate(Arrays.asList("setPublishRate"), outConsumer, errConsumer);

        assertTrue(errorMessages.get(0).contains("requires 1 argument"));
    }

    @Test
    @DisplayName("Test getStatus shows feed information")
    void testGetStatus() {
        feedWorker.addConfig(Arrays.asList("addConfig", "EURUSD", "1.08", "1.09", "0.0001", "0.0005", "100000", "1000000"),
                            outConsumer, errConsumer);
        feedWorker.addConfig(Arrays.asList("addConfig", "GBPUSD", "1.24", "1.26", "0.0002", "0.0008", "50000", "500000"),
                            outConsumer, errConsumer);
        outputMessages.clear();

        feedWorker.getStatus(Arrays.asList("getStatus"), outConsumer, errConsumer);

        String status = outputMessages.get(0);
        assertTrue(status.contains("Running"));
        assertTrue(status.contains("Publish Rate"));
        assertTrue(status.contains("Configured Symbols: 2"));
        assertTrue(status.contains("EURUSD"));
        assertTrue(status.contains("GBPUSD"));
    }

    @Test
    @DisplayName("Test listConfigs with no configurations")
    void testListConfigsEmpty() {
        feedWorker.listConfigs(Arrays.asList("listConfigs"), outConsumer, errConsumer);

        assertTrue(outputMessages.get(0).contains("No market data book configurations"));
    }

    @Test
    @DisplayName("Test listConfigs with multiple configurations")
    void testListConfigsMultiple() {
        feedWorker.addConfig(Arrays.asList("addConfig", "EURUSD", "1.08", "1.09", "0.0001", "0.0005", "100000", "1000000"),
                            outConsumer, errConsumer);
        feedWorker.addConfig(Arrays.asList("addConfig", "GBPUSD", "1.24", "1.26", "0.0002", "0.0008", "50000", "500000"),
                            outConsumer, errConsumer);
        outputMessages.clear();

        feedWorker.listConfigs(Arrays.asList("listConfigs"), outConsumer, errConsumer);

        String configs = outputMessages.get(0);
        assertTrue(configs.contains("EURUSD"));
        assertTrue(configs.contains("GBPUSD"));
        assertTrue(configs.contains("Price Range"));
        assertTrue(configs.contains("Spread Range"));
        assertTrue(configs.contains("Volume Range"));
    }

    @Test
    @DisplayName("Test addConfig with valid arguments")
    void testAddConfigValid() {
        feedWorker.addConfig(Arrays.asList("addConfig", "EURUSD", "1.08", "1.09", "0.0001", "0.0005", "100000", "1000000"),
                            outConsumer, errConsumer);

        assertEquals(1, feedWorker.getMarketDataBookConfigs().size());
        MarketDataBookConfig config = feedWorker.getMarketDataBookConfigs().get(0);
        assertEquals("EURUSD", config.getSymbol());
        assertEquals(1.08, config.getMinPrice());
        assertEquals(1.09, config.getMaxPrice());
        assertEquals(0.0001, config.getMinSpread());
        assertEquals(0.0005, config.getMaxSpread());
        assertEquals(100000, config.getMinVolume());
        assertEquals(1000000, config.getMaxVolume());
        assertTrue(outputMessages.get(0).contains("Added config for symbol: EURUSD"));
    }

    @Test
    @DisplayName("Test addConfig replaces existing config for same symbol")
    void testAddConfigReplaceExisting() {
        feedWorker.addConfig(Arrays.asList("addConfig", "EURUSD", "1.08", "1.09", "0.0001", "0.0005", "100000", "1000000"),
                            outConsumer, errConsumer);
        feedWorker.addConfig(Arrays.asList("addConfig", "EURUSD", "1.10", "1.11", "0.0002", "0.0006", "200000", "2000000"),
                            outConsumer, errConsumer);

        assertEquals(1, feedWorker.getMarketDataBookConfigs().size());
        MarketDataBookConfig config = feedWorker.getMarketDataBookConfigs().get(0);
        assertEquals(1.10, config.getMinPrice());
        assertEquals(1.11, config.getMaxPrice());
    }

    @Test
    @DisplayName("Test addConfig with insufficient arguments returns error")
    void testAddConfigInsufficientArguments() {
        feedWorker.addConfig(Arrays.asList("addConfig", "EURUSD", "1.08"), outConsumer, errConsumer);

        assertTrue(errorMessages.get(0).contains("requires 7 arguments"));
        assertEquals(0, feedWorker.getMarketDataBookConfigs().size());
    }

    @Test
    @DisplayName("Test addConfig with invalid numbers returns error")
    void testAddConfigInvalidNumbers() {
        feedWorker.addConfig(Arrays.asList("addConfig", "EURUSD", "abc", "1.09", "0.0001", "0.0005", "100000", "1000000"),
                            outConsumer, errConsumer);

        assertTrue(errorMessages.get(0).contains("Invalid number format"));
        assertEquals(0, feedWorker.getMarketDataBookConfigs().size());
    }

    @Test
    @DisplayName("Test removeConfig removes existing configuration")
    void testRemoveConfigExisting() {
        feedWorker.addConfig(Arrays.asList("addConfig", "EURUSD", "1.08", "1.09", "0.0001", "0.0005", "100000", "1000000"),
                            outConsumer, errConsumer);
        outputMessages.clear();

        feedWorker.removeConfig(Arrays.asList("removeConfig", "EURUSD"), outConsumer, errConsumer);

        assertEquals(0, feedWorker.getMarketDataBookConfigs().size());
        assertTrue(outputMessages.get(0).contains("Removed config for symbol: EURUSD"));
    }

    @Test
    @DisplayName("Test removeConfig with non-existent symbol returns error")
    void testRemoveConfigNonExistent() {
        feedWorker.removeConfig(Arrays.asList("removeConfig", "EURUSD"), outConsumer, errConsumer);

        assertTrue(errorMessages.get(0).contains("No config found"));
    }

    @Test
    @DisplayName("Test removeConfig with missing argument returns error")
    void testRemoveConfigMissingArgument() {
        feedWorker.removeConfig(Arrays.asList("removeConfig"), outConsumer, errConsumer);

        assertTrue(errorMessages.get(0).contains("requires 1 argument"));
    }

    @Test
    @DisplayName("Test clearAll removes all configurations")
    void testClearAll() {
        feedWorker.addConfig(Arrays.asList("addConfig", "EURUSD", "1.08", "1.09", "0.0001", "0.0005", "100000", "1000000"),
                            outConsumer, errConsumer);
        feedWorker.addConfig(Arrays.asList("addConfig", "GBPUSD", "1.24", "1.26", "0.0002", "0.0008", "50000", "500000"),
                            outConsumer, errConsumer);
        outputMessages.clear();

        feedWorker.clearAll(Arrays.asList("clearAll"), outConsumer, errConsumer);

        assertEquals(0, feedWorker.getMarketDataBookConfigs().size());
        assertTrue(outputMessages.get(0).contains("Cleared all 2"));
    }

    // ========== Fixed Pricing Commands Tests ==========

    @Test
    @DisplayName("Test setFixedSingleLevel creates correct configuration")
    void testSetFixedSingleLevel() {
        feedWorker.setFixedSingleLevel(
            Arrays.asList("setFixedSingleLevel", "EURUSD", "1.0850", "100000", "1.0853", "100000"),
            outConsumer, errConsumer);

        assertEquals(1, feedWorker.getMarketDataBookConfigs().size());
        MarketDataBookConfig config = feedWorker.getMarketDataBookConfigs().get(0);
        assertEquals("EURUSD", config.getSymbol());
        assertEquals(1.0850, config.getMinPrice());
        assertEquals(1.0850, config.getMaxPrice()); // Fixed price
        assertEquals(0.0003, config.getMinSpread(), 0.000001);
        assertEquals(0.0003, config.getMaxSpread(), 0.000001); // Fixed spread
        assertFalse(config.isMultilevel());
        assertTrue(outputMessages.get(0).contains("fixed single-level"));
    }

    @Test
    @DisplayName("Test setFixedSingleLevel with insufficient arguments returns error")
    void testSetFixedSingleLevelInsufficientArguments() {
        feedWorker.setFixedSingleLevel(Arrays.asList("setFixedSingleLevel", "EURUSD", "1.0850"),
                                       outConsumer, errConsumer);

        assertTrue(errorMessages.get(0).contains("requires 5 arguments"));
        assertEquals(0, feedWorker.getMarketDataBookConfigs().size());
    }

    @Test
    @DisplayName("Test setFixedSingleLevel with invalid numbers returns error")
    void testSetFixedSingleLevelInvalidNumbers() {
        feedWorker.setFixedSingleLevel(
            Arrays.asList("setFixedSingleLevel", "EURUSD", "abc", "100000", "1.0853", "100000"),
            outConsumer, errConsumer);

        assertTrue(errorMessages.get(0).contains("Invalid number format"));
        assertEquals(0, feedWorker.getMarketDataBookConfigs().size());
    }

    @Test
    @DisplayName("Test setFixedMultiLevel creates correct configuration")
    void testSetFixedMultiLevel() {
        feedWorker.setFixedMultiLevel(
            Arrays.asList("setFixedMultiLevel", "BTCUSD", "50000", "10", "1000000", "10", "5", "10000000"),
            outConsumer, errConsumer);

        assertEquals(1, feedWorker.getMarketDataBookConfigs().size());
        MarketDataBookConfig config = feedWorker.getMarketDataBookConfigs().get(0);
        assertEquals("BTCUSD", config.getSymbol());
        assertTrue(config.isMultilevel());
        assertEquals(10, config.getMultilevelDepth());
        assertNotNull(config.getMultilevelBookConfig());
        assertTrue(outputMessages.get(0).contains("fixed multi-level"));
        assertTrue(outputMessages.get(0).contains("depth: 10"));
    }

    @Test
    @DisplayName("Test setFixedMultiLevel with insufficient arguments returns error")
    void testSetFixedMultiLevelInsufficientArguments() {
        feedWorker.setFixedMultiLevel(Arrays.asList("setFixedMultiLevel", "BTCUSD", "50000"),
                                      outConsumer, errConsumer);

        assertTrue(errorMessages.get(0).contains("requires at least 7 arguments"));
        assertEquals(0, feedWorker.getMarketDataBookConfigs().size());
    }

    @Test
    @DisplayName("Test setFixedMultiLevel with invalid numbers returns error")
    void testSetFixedMultiLevelInvalidNumbers() {
        feedWorker.setFixedMultiLevel(
            Arrays.asList("setFixedMultiLevel", "BTCUSD", "abc", "10", "1000000", "10", "5", "10000000"),
            outConsumer, errConsumer);

        assertTrue(errorMessages.get(0).contains("Invalid number format"));
        assertEquals(0, feedWorker.getMarketDataBookConfigs().size());
    }

    // ========== JSON Configuration Tests ==========

    @Test
    @DisplayName("Test configureFromJson with valid JSON")
    void testConfigureFromJsonValid() {
        String json = "{\"symbol\":\"EURUSD\",\"minPrice\":1.08,\"maxPrice\":1.09," +
                     "\"minSpread\":0.0001,\"maxSpread\":0.0005," +
                     "\"minVolume\":100000,\"maxVolume\":1000000," +
                     "\"publishProbability\":1.0,\"multilevel\":false}";

        feedWorker.configureFromJson(Arrays.asList("configureFromJson", json), outConsumer, errConsumer);

        assertEquals(1, feedWorker.getMarketDataBookConfigs().size());
        MarketDataBookConfig config = feedWorker.getMarketDataBookConfigs().get(0);
        assertEquals("EURUSD", config.getSymbol());
        assertEquals(1.08, config.getMinPrice());
        assertEquals(1.09, config.getMaxPrice());
        assertTrue(outputMessages.get(0).contains("Configured symbol EURUSD from JSON"));
    }

    @Test
    @DisplayName("Test configureFromJson with invalid JSON returns error")
    void testConfigureFromJsonInvalid() {
        String json = "{invalid json}";

        feedWorker.configureFromJson(Arrays.asList("configureFromJson", json), outConsumer, errConsumer);

        assertTrue(errorMessages.get(0).contains("Failed to parse JSON"));
        assertEquals(0, feedWorker.getMarketDataBookConfigs().size());
    }

    @Test
    @DisplayName("Test configureFromJson with missing argument returns error")
    void testConfigureFromJsonMissingArgument() {
        feedWorker.configureFromJson(Arrays.asList("configureFromJson"), outConsumer, errConsumer);

        assertTrue(errorMessages.get(0).contains("requires 1 argument"));
    }

    @Test
    @DisplayName("Test configureFromJson sets feed and venue names if not provided")
    void testConfigureFromJsonSetsFeedAndVenueNames() {
        String json = "{\"symbol\":\"EURUSD\",\"minPrice\":1.08,\"maxPrice\":1.09," +
                     "\"minSpread\":0.0001,\"maxSpread\":0.0005," +
                     "\"minVolume\":100000,\"maxVolume\":1000000}";

        feedWorker.configureFromJson(Arrays.asList("configureFromJson", json), outConsumer, errConsumer);

        MarketDataBookConfig config = feedWorker.getMarketDataBookConfigs().get(0);
        assertEquals("mockMarketDataFeed", config.getFeedName());
        assertEquals("mockMarketDataFeed", config.getVenueName());
    }

    // ========== Admin Command Registration Tests ==========

    @Test
    @DisplayName("Test adminClient registers all 13 commands")
    void testAdminClientRegistersAllCommands() {
        List<String> registeredCommands = new ArrayList<>();

        AdminCommandRegistry registry = new AdminCommandRegistry() {
            @Override
            public <OUT, ERR> void registerCommand(String name, com.fluxtion.server.service.admin.AdminFunction<OUT, ERR> command) {
                registeredCommands.add(name);
            }

            @Override
            public void processAdminCommandRequest(com.fluxtion.server.service.admin.AdminCommandRequest request) {
            }

            @Override
            public List<String> commandList() {
                return List.of();
            }
        };

        feedWorker.adminClient(registry);

        // Verify all 13 commands are registered
        assertEquals(13, registeredCommands.size());
        assertTrue(registeredCommands.contains("mockMarketDataFeed.start"));
        assertTrue(registeredCommands.contains("mockMarketDataFeed.stop"));
        assertTrue(registeredCommands.contains("mockMarketDataFeed.pause"));
        assertTrue(registeredCommands.contains("mockMarketDataFeed.resume"));
        assertTrue(registeredCommands.contains("mockMarketDataFeed.setPublishRate"));
        assertTrue(registeredCommands.contains("mockMarketDataFeed.getStatus"));
        assertTrue(registeredCommands.contains("mockMarketDataFeed.listConfigs"));
        assertTrue(registeredCommands.contains("mockMarketDataFeed.addConfig"));
        assertTrue(registeredCommands.contains("mockMarketDataFeed.removeConfig"));
        assertTrue(registeredCommands.contains("mockMarketDataFeed.setFixedSingleLevel"));
        assertTrue(registeredCommands.contains("mockMarketDataFeed.setFixedMultiLevel"));
        assertTrue(registeredCommands.contains("mockMarketDataFeed.configureFromJson"));
        assertTrue(registeredCommands.contains("mockMarketDataFeed.clearAll"));
    }

    // ========== Integration Tests ==========

    @Test
    @DisplayName("Test complete workflow: add, list, pause, resume, remove")
    void testCompleteWorkflow() {
        // Add configs
        feedWorker.addConfig(Arrays.asList("addConfig", "EURUSD", "1.08", "1.09", "0.0001", "0.0005", "100000", "1000000"),
                            outConsumer, errConsumer);
        feedWorker.addConfig(Arrays.asList("addConfig", "GBPUSD", "1.24", "1.26", "0.0002", "0.0008", "50000", "500000"),
                            outConsumer, errConsumer);
        assertEquals(2, feedWorker.getMarketDataBookConfigs().size());

        // List configs
        outputMessages.clear();
        feedWorker.listConfigs(Arrays.asList("listConfigs"), outConsumer, errConsumer);
        assertTrue(outputMessages.get(0).contains("EURUSD"));
        assertTrue(outputMessages.get(0).contains("GBPUSD"));

        // Pause
        outputMessages.clear();
        feedWorker.pauseFeed(Arrays.asList("pause"), outConsumer, errConsumer);
        assertTrue(outputMessages.get(0).contains("paused"));

        // Resume
        outputMessages.clear();
        feedWorker.resumeFeed(Arrays.asList("resume"), outConsumer, errConsumer);
        assertTrue(outputMessages.get(0).contains("resumed"));

        // Remove one config
        outputMessages.clear();
        feedWorker.removeConfig(Arrays.asList("removeConfig", "EURUSD"), outConsumer, errConsumer);
        assertEquals(1, feedWorker.getMarketDataBookConfigs().size());
        assertTrue(outputMessages.get(0).contains("Removed"));

        // Clear all
        outputMessages.clear();
        feedWorker.clearAll(Arrays.asList("clearAll"), outConsumer, errConsumer);
        assertEquals(0, feedWorker.getMarketDataBookConfigs().size());
    }

    @Test
    @DisplayName("Test state transitions: start -> pause -> resume -> stop")
    void testStateTransitions() {
        // Start
        feedWorker.startFeed(Arrays.asList("start"), outConsumer, errConsumer);
        assertTrue(outputMessages.get(0).contains("started"));

        // Pause
        outputMessages.clear();
        feedWorker.pauseFeed(Arrays.asList("pause"), outConsumer, errConsumer);
        assertTrue(outputMessages.get(0).contains("paused"));

        // Resume
        outputMessages.clear();
        feedWorker.resumeFeed(Arrays.asList("resume"), outConsumer, errConsumer);
        assertTrue(outputMessages.get(0).contains("resumed"));

        // Stop
        outputMessages.clear();
        feedWorker.stopFeed(Arrays.asList("stop"), outConsumer, errConsumer);
        assertTrue(outputMessages.get(0).contains("stopped"));
    }

    @Test
    @DisplayName("Test multiple configs with different types")
    void testMultipleConfigsDifferentTypes() {
        // Add single-level config
        feedWorker.setFixedSingleLevel(
            Arrays.asList("setFixedSingleLevel", "EURUSD", "1.0850", "100000", "1.0853", "100000"),
            outConsumer, errConsumer);

        // Add multi-level config
        feedWorker.setFixedMultiLevel(
            Arrays.asList("setFixedMultiLevel", "BTCUSD", "50000", "10", "1000000", "10", "5", "10000000"),
            outConsumer, errConsumer);

        // Add random config
        feedWorker.addConfig(Arrays.asList("addConfig", "GBPUSD", "1.24", "1.26", "0.0002", "0.0008", "50000", "500000"),
                            outConsumer, errConsumer);

        assertEquals(3, feedWorker.getMarketDataBookConfigs().size());

        // Verify types in status
        outputMessages.clear();
        feedWorker.getStatus(Arrays.asList("getStatus"), outConsumer, errConsumer);
        String status = outputMessages.get(0);
        assertTrue(status.contains("EURUSD"));
        assertTrue(status.contains("BTCUSD"));
        assertTrue(status.contains("GBPUSD"));
    }
}
