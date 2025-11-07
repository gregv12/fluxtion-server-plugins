# Examples and Use Cases

Practical examples for using the Mock Market Data Feed in various scenarios.

## Table of Contents

- [Development Examples](#development-examples)
- [Testing Examples](#testing-examples)
- [Performance Testing Examples](#performance-testing-examples)
- [Demo and Training Examples](#demo-and-training-examples)
- [Integration Examples](#integration-examples)
- [Advanced Examples](#advanced-examples)

## Development Examples

### Example 1: Basic FX Pair Setup

**Scenario:** Setting up a simple EUR/USD feed for development.

**Setup:**
```bash
# Start the feed
mockMarketDataFeed.start

# Configure EUR/USD with realistic parameters
mockMarketDataFeed.addConfig EURUSD 1.0800 1.0900 0.0001 0.0005 100000 1000000

# Set comfortable update rate
mockMarketDataFeed.setPublishRate 2000

# Verify configuration
mockMarketDataFeed.getStatus
```

**Expected Output:**
```
Mock Market Data Feed Status
============================================================
Feed Name: mockMarketDataFeed
Running: true
Paused: false
Publish Rate: 2000 ms
Configured Symbols: 1

Symbols:
  - EURUSD (single-level)
============================================================
```

**Use:** Day-to-day development with realistic but slow updates.

---

### Example 2: Multi-Currency Portfolio

**Scenario:** Developing a multi-currency strategy.

**Setup:**
```bash
# Clear any existing config
mockMarketDataFeed.stop
mockMarketDataFeed.start

# Major FX pairs
mockMarketDataFeed.addConfig EURUSD 1.0800 1.0900 0.0001 0.0005 100000 1000000
mockMarketDataFeed.addConfig GBPUSD 1.2400 1.2600 0.0002 0.0008 50000 500000
mockMarketDataFeed.addConfig USDJPY 148.0 151.0 0.01 0.05 10000 100000
mockMarketDataFeed.addConfig AUDUSD 0.6400 0.6600 0.0001 0.0004 80000 800000
mockMarketDataFeed.addConfig USDCAD 1.3500 1.3700 0.0001 0.0004 80000 800000

# Review all configs
mockMarketDataFeed.listConfigs

# Set reasonable rate
mockMarketDataFeed.setPublishRate 1000
```

**Code Integration:**
```java
// Subscribe to all symbols
marketDataService.subscribe("mockMarketDataFeed", List.of(
    "EURUSD", "GBPUSD", "USDJPY", "AUDUSD", "USDCAD"
));

// Handle updates
@OnEventHandler
public void onMarketData(MarketDataBook book) {
    log.info("Received update: {} @ {}/{}",
        book.getSymbol(), book.getBid(), book.getAsk());
    // Process for strategy
}
```

---

### Example 3: Debugging with Fixed Prices

**Scenario:** Debugging a pricing bug that only occurs at specific prices.

**Setup:**
```bash
# Set exact prices that trigger the bug
mockMarketDataFeed.setFixedSingleLevel EURUSD 1.0850 250000 1.0853 250000

# Verify it's fixed
mockMarketDataFeed.listConfigs

# Run test
# Debug issue...

# Once fixed, switch back to random
mockMarketDataFeed.addConfig EURUSD 1.0800 1.0900 0.0001 0.0005 100000 1000000
```

**Use:** Reproducible testing environment for debugging.

---

## Testing Examples

### Example 4: Unit Testing with Deterministic Data

**Scenario:** Unit tests requiring predictable market data.

**Test Setup:**
```java
@BeforeEach
void setupMockFeed() {
    mockFeed = new MockMarketDataFeedWorker();

    // Use fixed prices for deterministic tests
    MarketDataBookConfig config = new MarketDataBookConfig();
    config.setSymbol("EURUSD");
    config.setMinPrice(1.0850);
    config.setMaxPrice(1.0850);  // Same as min = fixed
    config.setMinSpread(0.0003);
    config.setMaxSpread(0.0003);  // Same as min = fixed
    config.setMinVolume(250000);
    config.setMaxVolume(250000);  // Same as min = fixed
    config.setPublishProbability(1.0);  // Always publish

    mockFeed.setMarketDataBookConfigs(List.of(config));
    mockFeed.start();
}

@Test
void testPricingLogic() {
    // Market data will always be: bid=1.0850, ask=1.0853, volume=250000

    // Wait for first publish
    await().atMost(2, SECONDS).until(() ->
        marketDataService.getLatestBook("EURUSD") != null
    );

    MarketDataBook book = marketDataService.getLatestBook("EURUSD");

    // Assert exact values
    assertEquals(1.0850, book.getBid(), 0.0);
    assertEquals(1.0853, book.getAsk(), 0.0);
    assertEquals(250000, book.getBidVolume(), 0.0);

    // Test pricing logic with known inputs
    double customerPrice = pricingEngine.calculatePrice("EURUSD", Direction.BUY, 100000);
    assertEquals(1.0849, customerPrice, 0.0001);  // Expected based on 1bp markup
}
```

---

### Example 5: Integration Testing with Realistic Variation

**Scenario:** Integration tests with realistic price movements.

**Test Setup:**
```java
@BeforeEach
void setupIntegrationTest() {
    mockFeed = new MockMarketDataFeedWorker();

    // Use realistic ranges with variation
    MarketDataBookConfig config = new MarketDataBookConfig();
    config.setSymbol("EURUSD");
    config.setMinPrice(1.0800);
    config.setMaxPrice(1.0900);  // 100 pip range
    config.setMinSpread(0.0001);
    config.setMaxSpread(0.0005);  // Variable spread
    config.setMinVolume(100000);
    config.setMaxVolume(1000000);
    config.setPublishProbability(0.9);  // Occasional gaps

    mockFeed.setMarketDataBookConfigs(List.of(config));
    mockFeed.setPublishRateMillis(100);  // Fast updates
    mockFeed.start();
}

@Test
void testStrategyUnderVolatileConditions() {
    // Let strategy run for 10 seconds with variable market data
    await().atMost(10, SECONDS).until(() ->
        strategy.getExecutedOrderCount() > 0
    );

    // Verify strategy behavior
    assertTrue(strategy.hasProfit());
    assertTrue(strategy.getMeanSpreadCaptured() > 0.0001);
}
```

---

### Example 6: Market Impact Testing with Multi-Level Book

**Scenario:** Testing how an algorithm handles market depth.

**Setup:**
```bash
# Configure deep book
mockMarketDataFeed.setFixedMultiLevel EURUSD 1.0850 0.0003 500000 20 0.0001 20000000
```

**Test Code:**
```java
@Test
void testMarketImpact() {
    // Multi-level book configured with 20 levels

    MultilevelMarketDataBook book = (MultilevelMarketDataBook)
        marketDataService.getLatestBook("EURUSD");

    // Test various order sizes
    double impact100K = calculateImpact(book, 100000);
    double impact500K = calculateImpact(book, 500000);
    double impact1M = calculateImpact(book, 1000000);

    // Verify impact increases with size
    assertTrue(impact500K > impact100K);
    assertTrue(impact1M > impact500K);

    // Verify algorithm respects liquidity
    Order order = strategy.createOrder("EURUSD", 1000000, Direction.BUY);
    assertTrue(order.isSliced());  // Should slice large order
    assertEquals(10, order.getChildOrders().size());  // Into 10 pieces
}

private double calculateImpact(MultilevelMarketDataBook book, double quantity) {
    double bestBid = book.getBestBid().getPrice();
    double worstBid = book.getWorstBidPriceForQuantity(quantity);
    return (bestBid - worstBid) / bestBid * 10000;  // In basis points
}
```

---

## Performance Testing Examples

### Example 7: Load Testing with High Frequency

**Scenario:** Testing system performance under high message rates.

**Setup:**
```bash
# Start fresh
mockMarketDataFeed.stop
mockMarketDataFeed.start

# Add 50 symbols
for i in {1..50}; do
  mockMarketDataFeed.addConfig PAIR$i 100 110 0.01 0.05 1000 10000
done

# Set aggressive publish rate
mockMarketDataFeed.setPublishRate 10  # 100 messages/second

# Verify
mockMarketDataFeed.getStatus
```

**Monitoring:**
```java
@Test
void testHighFrequencyPerformance() {
    // Metrics collection
    AtomicLong messageCount = new AtomicLong(0);
    AtomicLong totalLatency = new AtomicLong(0);

    // Subscribe to all symbols
    marketDataService.subscribe("mockMarketDataFeed", getAllSymbols());

    // Collect metrics
    marketDataService.addListener(book -> {
        long latency = System.nanoTime() - book.getTimestamp();
        messageCount.incrementAndGet();
        totalLatency.addAndGet(latency);
    });

    // Run for 60 seconds
    await().atMost(60, SECONDS).until(() ->
        messageCount.get() > 6000  // 50 symbols * 2 updates/sec * 60 sec
    );

    // Analyze performance
    long avgLatency = totalLatency.get() / messageCount.get();
    log.info("Processed {} messages with avg latency {}us",
        messageCount.get(), avgLatency / 1000);

    assertTrue(avgLatency < 1_000_000);  // < 1ms average
}
```

---

### Example 8: Stress Testing with Multi-Level Books

**Scenario:** Stress test with deep order books.

**Setup:**
```java
void setupStressTest() {
    mockFeed = new MockMarketDataFeedWorker();

    List<MarketDataBookConfig> configs = new ArrayList<>();

    // 100 symbols with 50-level books
    for (int i = 0; i < 100; i++) {
        MarketDataBookConfig config = new MarketDataBookConfig();
        config.setSymbol("STRESS_" + i);
        config.setMinPrice(100.0);
        config.setMaxPrice(110.0);
        config.setMinSpread(0.01);
        config.setMaxSpread(0.05);
        config.setMinVolume(1000);
        config.setMaxVolume(10000);
        config.setPublishProbability(1.0);
        config.setMultilevel(true);
        config.setMultilevelDepth(50);  // Deep books

        configs.add(config);
    }

    mockFeed.setMarketDataBookConfigs(configs);
    mockFeed.setPublishRateMillis(10);  // Very aggressive
    mockFeed.start();
}

@Test
void testSystemUnderStress() {
    // System should handle: 100 symbols * 100 updates/sec * 50 levels
    // = 500,000 price level updates per second

    // Monitor memory and CPU
    MemoryMonitor memMonitor = new MemoryMonitor();
    CPUMonitor cpuMonitor = new CPUMonitor();

    // Run for 5 minutes
    await().atMost(5, MINUTES).pollInterval(1, SECONDS).until(() -> {
        assertTrue(memMonitor.getHeapUsage() < 0.9);  // < 90% heap
        assertTrue(cpuMonitor.getUsage() < 0.8);  // < 80% CPU
        return false;  // Keep running
    });
}
```

---

## Demo and Training Examples

### Example 9: Live Demo with Control

**Scenario:** Live demonstration with ability to pause/resume.

**Demo Script:**
```bash
# Setup initial state
mockMarketDataFeed.stop
mockMarketDataFeed.start

# "Let me show you how market data flows..."
mockMarketDataFeed.setFixedSingleLevel EURUSD 1.0850 250000 1.0853 250000
mockMarketDataFeed.setPublishRate 3000

# "Notice the bid and ask prices... they're stable for clarity"
# ... explain concepts ...

# "Now let's pause to discuss"
mockMarketDataFeed.pause

# ... discussion ...

# "Let's see it in action again"
mockMarketDataFeed.resume

# "Now I'll show you what happens with price variation"
mockMarketDataFeed.addConfig EURUSD 1.0800 1.0900 0.0001 0.0005 100000 1000000

# "Watch how the prices move within this range..."
# ... continue demo ...

# "For the next section, let me speed this up"
mockMarketDataFeed.setPublishRate 500
```

---

### Example 10: Training Environment

**Scenario:** Training environment for traders learning market making.

**Setup:**
```bash
# Create a realistic FX trading environment
mockMarketDataFeed.stop
mockMarketDataFeed.start

# Major pairs with realistic parameters
mockMarketDataFeed.addConfig EURUSD 1.0800 1.0900 0.0001 0.0005 100000 1000000
mockMarketDataFeed.addConfig GBPUSD 1.2400 1.2600 0.0002 0.0008 50000 500000
mockMarketDataFeed.addConfig USDJPY 148.0 151.0 0.01 0.05 10000 100000

# Moderate update rate for training
mockMarketDataFeed.setPublishRate 2000

# Create a scenario: volatile period
# Wider spreads simulate volatility
mockMarketDataFeed.addConfig EURUSD 1.0800 1.0900 0.0003 0.0010 100000 1000000

# Create a scenario: quiet period
# Tighter spreads simulate quiet market
mockMarketDataFeed.addConfig EURUSD 1.0800 1.0900 0.0001 0.0002 100000 1000000
```

**Training Module:**
```java
public class MarketMakingTrainer {

    public void runScenario1_NormalConditions() {
        configureNormalMarket();
        instructions.show("Try to maintain a 2 pip spread while managing inventory");
        simulator.run(Duration.ofMinutes(10));
        reportCard.show(simulator.getResults());
    }

    public void runScenario2_VolatileMarket() {
        configureVolatileMarket();
        instructions.show("Widen your spreads to manage risk during volatility");
        simulator.run(Duration.ofMinutes(10));
        reportCard.show(simulator.getResults());
    }

    private void configureNormalMarket() {
        admin.execute("mockMarketDataFeed.addConfig EURUSD 1.0800 1.0900 0.0001 0.0003 100000 1000000");
        admin.execute("mockMarketDataFeed.setPublishRate 1000");
    }

    private void configureVolatileMarket() {
        admin.execute("mockMarketDataFeed.addConfig EURUSD 1.0700 1.1000 0.0005 0.0020 50000 500000");
        admin.execute("mockMarketDataFeed.setPublishRate 500");
    }
}
```

---

## Integration Examples

### Example 11: Integration with Pricing Engine

**Scenario:** Integration test for pricing engine consuming market data.

**Setup:**
```java
@SpringBootTest
public class PricingEngineIntegrationTest {

    @Autowired
    private PricingEngine pricingEngine;

    @Autowired
    private MockMarketDataFeedWorker mockFeed;

    @BeforeEach
    void setup() {
        // Configure market data
        MarketDataBookConfig config = new MarketDataBookConfig();
        config.setSymbol("EURUSD");
        config.setMinPrice(1.0850);
        config.setMaxPrice(1.0850);
        config.setMinSpread(0.0003);
        config.setMaxSpread(0.0003);
        config.setMinVolume(250000);
        config.setMaxVolume(250000);
        config.setPublishProbability(1.0);

        mockFeed.setMarketDataBookConfigs(List.of(config));
        mockFeed.setPublishRateMillis(100);
        mockFeed.start();
    }

    @Test
    void testPricingEngineIntegration() {
        // Wait for market data
        await().atMost(2, SECONDS).until(() ->
            pricingEngine.hasPrice("EURUSD")
        );

        // Request customer price
        CustomerPriceRequest request = CustomerPriceRequest.builder()
            .customerId("CUST001")
            .symbol("EURUSD")
            .side(Direction.BUY)
            .quantity(100000)
            .build();

        CustomerPrice price = pricingEngine.getPrice(request);

        // Verify price is based on market data (1.0850 bid)
        // with customer markup
        assertEquals(1.0849, price.getPrice(), 0.0001);
        assertTrue(price.isValid());
    }
}
```

---

### Example 12: Integration with Order Management System

**Scenario:** Testing OMS with simulated market data.

**Setup:**
```java
@Test
void testOMSWithMarketData() {
    // Setup multi-level market for testing order slicing
    admin.execute(
        "mockMarketDataFeed.setFixedMultiLevel EURUSD 1.0850 0.0003 500000 10 0.0001 10000000"
    );

    // Submit large order
    Order order = Order.builder()
        .symbol("EURUSD")
        .side(Direction.BUY)
        .quantity(2000000)  // Larger than any single level
        .type(OrderType.MARKET)
        .build();

    OrderResponse response = oms.submitOrder(order);

    // Verify OMS sliced the order
    assertTrue(response.isSliced());
    assertEquals(4, response.getChildOrders().size());

    // Verify execution used market depth
    double avgPrice = response.getAveragePrice();
    assertTrue(avgPrice > 1.0850);  // Worse than best bid due to depth
    assertTrue(avgPrice < 1.0853);  // But better than ask
}
```

---

## Advanced Examples

### Example 13: Dynamic Reconfiguration

**Scenario:** Changing market conditions during runtime.

**Implementation:**
```java
public class DynamicMarketSimulator {

    private final MockMarketDataFeedWorker mockFeed;
    private final AdminCommandRegistry admin;

    public void simulateMarketDay() {
        // 9:00 AM - Market opens, quiet
        configureQuietMarket();
        Thread.sleep(Duration.ofMinutes(30));

        // 9:30 AM - Liquidity increases
        configureNormalMarket();
        Thread.sleep(Duration.ofHours(2));

        // 11:30 AM - Volatility spike (news)
        configureVolatileMarket();
        Thread.sleep(Duration.ofMinutes(30));

        // 12:00 PM - Back to normal
        configureNormalMarket();
        Thread.sleep(Duration.ofHours(3));

        // 3:00 PM - End of day, liquidity dries up
        configureLowLiquidityMarket();
        Thread.sleep(Duration.ofMinutes(30));

        // 3:30 PM - Market closes
        mockFeed.stop();
    }

    private void configureQuietMarket() {
        admin.execute("mockMarketDataFeed.addConfig EURUSD 1.0845 1.0855 0.0001 0.0002 50000 200000");
        admin.execute("mockMarketDataFeed.setPublishRate 2000");
    }

    private void configureNormalMarket() {
        admin.execute("mockMarketDataFeed.addConfig EURUSD 1.0800 1.0900 0.0001 0.0005 100000 1000000");
        admin.execute("mockMarketDataFeed.setPublishRate 1000");
    }

    private void configureVolatileMarket() {
        admin.execute("mockMarketDataFeed.addConfig EURUSD 1.0700 1.1000 0.0005 0.0020 50000 500000");
        admin.execute("mockMarketDataFeed.setPublishRate 500");
    }

    private void configureLowLiquidityMarket() {
        admin.execute("mockMarketDataFeed.addConfig EURUSD 1.0830 1.0870 0.0003 0.0010 20000 100000");
        admin.execute("mockMarketDataFeed.setPublishRate 3000");
    }
}
```

---

### Example 14: JSON-Based Configuration Management

**Scenario:** Managing configurations as JSON for version control.

**Configuration File (eurusd-config.json):**
```json
{
  "symbol": "EURUSD",
  "minPrice": 1.0800,
  "maxPrice": 1.0900,
  "minSpread": 0.0001,
  "maxSpread": 0.0005,
  "minVolume": 100000,
  "maxVolume": 1000000,
  "publishProbability": 0.95,
  "precisionDpsPrice": 5,
  "precisionDpsVolume": 0,
  "multilevel": true,
  "multilevelDepth": 15
}
```

**Loading Configurations:**
```java
public class ConfigurationManager {

    private final MockMarketDataFeedWorker mockFeed;
    private final ObjectMapper mapper = new ObjectMapper();

    public void loadConfigurationsFromDirectory(Path configDir) throws IOException {
        List<MarketDataBookConfig> configs = new ArrayList<>();

        Files.list(configDir)
            .filter(p -> p.toString().endsWith(".json"))
            .forEach(path -> {
                try {
                    String json = Files.readString(path);
                    MarketDataBookConfig config = mapper.readValue(
                        json, MarketDataBookConfig.class
                    );
                    configs.add(config);
                    log.info("Loaded config for {}", config.getSymbol());
                } catch (IOException e) {
                    log.error("Failed to load config from {}", path, e);
                }
            });

        mockFeed.setMarketDataBookConfigs(configs);
        log.info("Loaded {} configurations", configs.size());
    }

    public void saveConfiguration(MarketDataBookConfig config, Path outputPath)
        throws IOException {
        String json = mapper.writerWithDefaultPrettyPrinter()
            .writeValueAsString(config);
        Files.writeString(outputPath, json);
    }
}
```

---

### Example 15: Automated Testing Pipeline

**Scenario:** CI/CD pipeline with automated market data testing.

**Test Configuration:**
```yaml
# test-config.yaml
market-data-tests:
  - name: "Basic Functionality"
    symbols:
      - symbol: "EURUSD"
        minPrice: 1.08
        maxPrice: 1.09
        minSpread: 0.0001
        maxSpread: 0.0005
    publishRate: 1000
    duration: 60
    assertions:
      - messageCount > 50
      - avgLatency < 10ms

  - name: "High Frequency"
    symbols:
      - symbol: "TEST1"
        fixed: true
        bidPrice: 100.0
        askPrice: 100.1
    publishRate: 10
    duration: 60
    assertions:
      - messageCount > 5000
      - maxLatency < 50ms

  - name: "Multi-Level Depth"
    symbols:
      - symbol: "EURUSD"
        multilevel: true
        depth: 20
        midPrice: 1.085
    publishRate: 100
    duration: 30
    assertions:
      - allBooksHaveDepth >= 20
      - liquidityDistributionIsRealistic
```

**Test Runner:**
```java
@SpringBootTest
public class AutomatedMarketDataTests {

    @Autowired
    private MockMarketDataFeedWorker mockFeed;

    @ParameterizedTest
    @ValueSource(strings = {"test-config.yaml"})
    void runAutomatedTests(String configFile) throws Exception {
        TestConfiguration config = loadTestConfig(configFile);

        for (TestCase testCase : config.getTestCases()) {
            log.info("Running test: {}", testCase.getName());

            // Setup
            mockFeed.stop();
            mockFeed.start();
            configureFromTestCase(testCase);

            // Run
            TestResults results = runTest(testCase);

            // Assert
            assertTestResults(testCase, results);

            log.info("Test passed: {}", testCase.getName());
        }
    }

    private void assertTestResults(TestCase testCase, TestResults results) {
        for (Assertion assertion : testCase.getAssertions()) {
            assertTrue(
                assertion.evaluate(results),
                "Assertion failed: " + assertion
            );
        }
    }
}
```

---

## Summary

These examples demonstrate:

1. **Development**: Quick setup for daily development work
2. **Testing**: Unit, integration, and system testing approaches
3. **Performance**: Load and stress testing methodologies
4. **Demo**: Interactive demonstration and training scenarios
5. **Integration**: Real-world integration patterns
6. **Advanced**: Dynamic configuration and automation

## Related Documentation

- [Mock Market Data Feed Guide](MockMarketDataFeed.md)
- [Admin Commands Reference](AdminCommands.md)
- [Configuration Guide](Configuration.md)
