package com.fluxtion.server.plugin.trading.component.mockvenue.mktdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.server.plugin.trading.component.marketdatafeed.AbstractMarketDataFeedWorker;
import com.fluxtion.server.plugin.trading.service.marketdata.MarketConnected;
import com.fluxtion.server.plugin.trading.service.marketdata.MarketDataBook;
import com.fluxtion.server.plugin.trading.service.marketdata.MarketFeedEvent;
import com.fluxtion.server.plugin.trading.service.marketdata.MultilevelMarketDataBook;
import com.fluxtion.server.plugin.trading.service.marketdata.MultilevelBookConfig;
import com.fluxtion.server.service.admin.AdminCommandRegistry;
import com.fluxtion.server.service.scheduler.SchedulerService;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Log4j2
public class MockMarketDataFeedWorker extends AbstractMarketDataFeedWorker {

    public static final String MOCK_MARKET_DATA_FEED = "mockMarketDataFeed";
    private SchedulerService schedulerService;
    private volatile MarketConnected marketConnected;
    private volatile boolean running = true;
    private volatile boolean paused = false;
    private final Map<String, MarketDataBookConfig> configMap = new HashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Getter
    @Setter
    private int publishRateMillis = 1000;
    @Getter
    @Setter
    private List<MarketDataBookConfig> marketDataBookConfigs = new ArrayList<>();

    public MockMarketDataFeedWorker(String roleName) {
        super(roleName);
        setFeedName(roleName);
    }

    public MockMarketDataFeedWorker() {
        super(MOCK_MARKET_DATA_FEED);
        setFeedName(MOCK_MARKET_DATA_FEED);
    }

    @Override
    public void start() {
        super.start();
        log.info("Starting MockMarketDataFeedWorker publishRateMillis {}", publishRateMillis);
    }

    @ServiceRegistered
    public void adminClient(AdminCommandRegistry adminCommandRegistry) {
        log.info("admin client registered {}", adminCommandRegistry);
        adminCommandRegistry.registerCommand(getFeedName() + ".start", this::startFeed);
        adminCommandRegistry.registerCommand(getFeedName() + ".stop", this::stopFeed);
        adminCommandRegistry.registerCommand(getFeedName() + ".pause", this::pauseFeed);
        adminCommandRegistry.registerCommand(getFeedName() + ".resume", this::resumeFeed);
        adminCommandRegistry.registerCommand(getFeedName() + ".setPublishRate", this::setPublishRate);
        adminCommandRegistry.registerCommand(getFeedName() + ".getStatus", this::getStatus);
        adminCommandRegistry.registerCommand(getFeedName() + ".listConfigs", this::listConfigs);
        adminCommandRegistry.registerCommand(getFeedName() + ".addConfig", this::addConfig);
        adminCommandRegistry.registerCommand(getFeedName() + ".removeConfig", this::removeConfig);
        adminCommandRegistry.registerCommand(getFeedName() + ".setFixedSingleLevel", this::setFixedSingleLevel);
        adminCommandRegistry.registerCommand(getFeedName() + ".setFixedMultiLevel", this::setFixedMultiLevel);
        adminCommandRegistry.registerCommand(getFeedName() + ".configureFromJson", this::configureFromJson);
        adminCommandRegistry.registerCommand(getFeedName() + ".clearAll", this::clearAll);
    }

    @ServiceRegistered
    public void scheduler(SchedulerService schedulerService) {
        log.info("Scheduler service started");
        this.schedulerService = schedulerService;
        marketConnected = new MarketConnected(getFeedName());

        // Initialize configMap with any pre-configured books
        for (MarketDataBookConfig config : marketDataBookConfigs) {
            configMap.put(config.getSymbol(), config);
        }

        this.schedulerService.scheduleAfterDelay(publishRateMillis, this::timerTriggered);
    }

    public void timerTriggered() {
        this.schedulerService.scheduleAfterDelay(publishRateMillis, this::timerTriggered);
        log.trace("timer triggered {}", schedulerService);

        // Check if running and not paused
        if (!running || paused) {
            log.trace("Feed not running or paused - skipping publish");
            return;
        }

        if(marketConnected != null) {
            log.info("publish marketConnected {}", marketConnected);
            publish(marketConnected);
            marketConnected = null;
        }

        for (MarketDataBookConfig config : marketDataBookConfigs) {
            MarketFeedEvent marketFeedEvent;
            if (config.isMultilevel()) {
                marketFeedEvent = MarketDataBookGenerator.generateRandomMultilevel(
                        config,
                        config.getMultilevelDepth(),
                        config.getMultilevelBookConfig()
                );
            } else {
                marketFeedEvent = MarketDataBookGenerator.generateRandom(config);
            }

            if (marketFeedEvent != null) {
                log.debug("publishPrice:{}", marketFeedEvent);
                publish(marketFeedEvent);
            }
        }
    }

    @Override
    protected void subscribeToSymbol(String feedName, String venueName, String symbol) {
        log.info("service:{} subscribeToSymbol:{} venueName:{} feedName:{}", getServiceName(), symbol, venueName, feedName);
//        marketConnected = new MarketConnected(getFeedName());
    }

    @Override
    public int doWork() throws Exception {
        return 0;
    }

    // ========== Admin Command Implementations ==========

    protected void startFeed(List<String> args, Consumer<String> out, Consumer<String> err) {
        running = true;
        paused = false;
        marketConnected = new MarketConnected(getFeedName());
        out.accept("Mock market data feed started");
        log.info("Mock market data feed started");
    }

    protected void stopFeed(List<String> args, Consumer<String> out, Consumer<String> err) {
        running = false;
        paused = false;
        // Clear all books
        marketDataBookConfigs.clear();
        configMap.clear();
        out.accept("Mock market data feed stopped and all configs cleared");
        log.info("Mock market data feed stopped");
    }

    protected void pauseFeed(List<String> args, Consumer<String> out, Consumer<String> err) {
        if (!running) {
            err.accept("Feed is not running - cannot pause");
            return;
        }
        paused = true;
        out.accept("Mock market data feed paused");
        log.info("Mock market data feed paused");
    }

    protected void resumeFeed(List<String> args, Consumer<String> out, Consumer<String> err) {
        if (!running) {
            err.accept("Feed is not running - use start command instead");
            return;
        }
        paused = false;
        out.accept("Mock market data feed resumed");
        log.info("Mock market data feed resumed");
    }

    protected void setPublishRate(List<String> args, Consumer<String> out, Consumer<String> err) {
        if (args.size() != 2) {
            err.accept("setPublishRate requires 1 argument [rateMillis]");
            return;
        }

        try {
            int newRate = Integer.parseInt(args.get(1));
            if (newRate < 1) {
                err.accept("Publish rate must be positive");
                return;
            }
            int oldRate = publishRateMillis;
            publishRateMillis = newRate;
            out.accept(String.format("Publish rate changed from %dms to %dms", oldRate, newRate));
            log.info("Publish rate changed from {}ms to {}ms", oldRate, newRate);
        } catch (NumberFormatException e) {
            err.accept("Invalid number format for publish rate: " + args.get(1));
        }
    }

    protected void getStatus(List<String> args, Consumer<String> out, Consumer<String> err) {
        StringBuilder status = new StringBuilder();
        status.append("Mock Market Data Feed Status").append(System.lineSeparator());
        status.append("=".repeat(60)).append(System.lineSeparator());
        status.append(String.format("Feed Name: %s", getFeedName())).append(System.lineSeparator());
        status.append(String.format("Running: %s", running)).append(System.lineSeparator());
        status.append(String.format("Paused: %s", paused)).append(System.lineSeparator());
        status.append(String.format("Publish Rate: %d ms", publishRateMillis)).append(System.lineSeparator());
        status.append(String.format("Configured Symbols: %d", marketDataBookConfigs.size())).append(System.lineSeparator());

        if (!marketDataBookConfigs.isEmpty()) {
            status.append(System.lineSeparator()).append("Symbols:").append(System.lineSeparator());
            for (MarketDataBookConfig config : marketDataBookConfigs) {
                status.append(String.format("  - %s (%s)", config.getSymbol(),
                        config.isMultilevel() ? "multilevel-" + config.getMultilevelDepth() : "single-level"))
                        .append(System.lineSeparator());
            }
        }
        status.append("=".repeat(60));

        out.accept(status.toString());
    }

    protected void listConfigs(List<String> args, Consumer<String> out, Consumer<String> err) {
        if (marketDataBookConfigs.isEmpty()) {
            out.accept("No market data book configurations");
            return;
        }

        StringBuilder report = new StringBuilder();
        report.append("Market Data Book Configurations").append(System.lineSeparator());
        report.append("=".repeat(80)).append(System.lineSeparator());

        for (MarketDataBookConfig config : marketDataBookConfigs) {
            report.append(String.format("Symbol: %s", config.getSymbol())).append(System.lineSeparator());
            report.append(String.format("  Type: %s", config.isMultilevel() ? "Multi-level" : "Single-level"))
                    .append(System.lineSeparator());
            report.append(String.format("  Price Range: %.6f - %.6f", config.getMinPrice(), config.getMaxPrice()))
                    .append(System.lineSeparator());
            report.append(String.format("  Spread Range: %.6f - %.6f", config.getMinSpread(), config.getMaxSpread()))
                    .append(System.lineSeparator());
            report.append(String.format("  Volume Range: %.2f - %.2f", config.getMinVolume(), config.getMaxVolume()))
                    .append(System.lineSeparator());
            report.append(String.format("  Publish Probability: %.2f", config.getPublishProbability()))
                    .append(System.lineSeparator());
            if (config.isMultilevel()) {
                report.append(String.format("  Depth: %d levels", config.getMultilevelDepth()))
                        .append(System.lineSeparator());
            }
            report.append(System.lineSeparator());
        }

        out.accept(report.toString());
    }

    protected void addConfig(List<String> args, Consumer<String> out, Consumer<String> err) {
        if (args.size() < 8) {
            err.accept("addConfig requires 7 arguments: [symbol] [minPrice] [maxPrice] [minSpread] [maxSpread] [minVolume] [maxVolume]");
            return;
        }

        try {
            String symbol = args.get(1);
            double minPrice = Double.parseDouble(args.get(2));
            double maxPrice = Double.parseDouble(args.get(3));
            double minSpread = Double.parseDouble(args.get(4));
            double maxSpread = Double.parseDouble(args.get(5));
            double minVolume = Double.parseDouble(args.get(6));
            double maxVolume = Double.parseDouble(args.get(7));

            MarketDataBookConfig config = new MarketDataBookConfig();
            config.setSymbol(symbol);
            config.setFeedName(getFeedName());
            config.setVenueName(getFeedName());
            config.setMinPrice(minPrice);
            config.setMaxPrice(maxPrice);
            config.setMinSpread(minSpread);
            config.setMaxSpread(maxSpread);
            config.setMinVolume(minVolume);
            config.setMaxVolume(maxVolume);
            config.setPublishProbability(1.0);

            // Remove existing config if present
            marketDataBookConfigs.removeIf(c -> c.getSymbol().equals(symbol));
            marketDataBookConfigs.add(config);
            configMap.put(symbol, config);

            out.accept(String.format("Added config for symbol: %s", symbol));
            log.info("Added config for symbol: {}", symbol);
        } catch (NumberFormatException e) {
            err.accept("Invalid number format in arguments: " + e.getMessage());
        }
    }

    protected void removeConfig(List<String> args, Consumer<String> out, Consumer<String> err) {
        if (args.size() != 2) {
            err.accept("removeConfig requires 1 argument [symbol]");
            return;
        }

        String symbol = args.get(1);
        boolean removed = marketDataBookConfigs.removeIf(c -> c.getSymbol().equals(symbol));
        configMap.remove(symbol);

        if (removed) {
            out.accept(String.format("Removed config for symbol: %s", symbol));
            log.info("Removed config for symbol: {}", symbol);
        } else {
            err.accept(String.format("No config found for symbol: %s", symbol));
        }
    }

    protected void setFixedSingleLevel(List<String> args, Consumer<String> out, Consumer<String> err) {
        if (args.size() != 6) {
            err.accept("setFixedSingleLevel requires 5 arguments: [symbol] [bidPrice] [bidVolume] [askPrice] [askVolume]");
            return;
        }

        try {
            String symbol = args.get(1);
            double bidPrice = Double.parseDouble(args.get(2));
            double bidVolume = Double.parseDouble(args.get(3));
            double askPrice = Double.parseDouble(args.get(4));
            double askVolume = Double.parseDouble(args.get(5));

            MarketDataBookConfig config = new MarketDataBookConfig();
            config.setSymbol(symbol);
            config.setFeedName(getFeedName());
            config.setVenueName(getFeedName());
            config.setMinPrice(bidPrice);
            config.setMaxPrice(bidPrice);
            config.setMinSpread(askPrice - bidPrice);
            config.setMaxSpread(askPrice - bidPrice);
            config.setMinVolume(Math.min(bidVolume, askVolume));
            config.setMaxVolume(Math.max(bidVolume, askVolume));
            config.setPublishProbability(1.0);
            config.setMultilevel(false);

            // Remove existing config if present
            marketDataBookConfigs.removeIf(c -> c.getSymbol().equals(symbol));
            marketDataBookConfigs.add(config);
            configMap.put(symbol, config);

            out.accept(String.format("Set fixed single-level config for symbol: %s (bid: %.6f@%.2f, ask: %.6f@%.2f)",
                    symbol, bidPrice, bidVolume, askPrice, askVolume));
            log.info("Set fixed single-level config for symbol: {}", symbol);
        } catch (NumberFormatException e) {
            err.accept("Invalid number format in arguments: " + e.getMessage());
        }
    }

    protected void setFixedMultiLevel(List<String> args, Consumer<String> out, Consumer<String> err) {
        if (args.size() < 8) {
            err.accept("setFixedMultiLevel requires at least 7 arguments: [symbol] [midPrice] [spread] [volume] [depth] [tickSize] [maxQuantityOnSide]");
            return;
        }

        try {
            String symbol = args.get(1);
            double midPrice = Double.parseDouble(args.get(2));
            double spread = Double.parseDouble(args.get(3));
            double volume = Double.parseDouble(args.get(4));
            int depth = Integer.parseInt(args.get(5));
            double tickSize = Double.parseDouble(args.get(6));
            double maxQuantity = Double.parseDouble(args.get(7));

            MarketDataBookConfig config = new MarketDataBookConfig();
            config.setSymbol(symbol);
            config.setFeedName(getFeedName());
            config.setVenueName(getFeedName());
            config.setMinPrice(midPrice - (spread / 2.0));
            config.setMaxPrice(midPrice - (spread / 2.0));
            config.setMinSpread(spread);
            config.setMaxSpread(spread);
            config.setMinVolume(volume * 0.8);
            config.setMaxVolume(volume * 1.2);
            config.setPublishProbability(1.0);
            config.setMultilevel(true);
            config.setMultilevelDepth(depth);

            MultilevelBookConfig bookConfig = MultilevelBookConfig.builder()
                    .maxDepth(Math.max(depth, 20))
                    .build();
            config.setMultilevelBookConfig(bookConfig);

            // Remove existing config if present
            marketDataBookConfigs.removeIf(c -> c.getSymbol().equals(symbol));
            marketDataBookConfigs.add(config);
            configMap.put(symbol, config);

            out.accept(String.format("Set fixed multi-level config for symbol: %s (depth: %d, mid: %.6f, spread: %.6f, volume: %.2f)",
                    symbol, depth, midPrice, spread, volume));
            log.info("Set fixed multi-level config for symbol: {}", symbol);
        } catch (NumberFormatException e) {
            err.accept("Invalid number format in arguments: " + e.getMessage());
        }
    }

    protected void configureFromJson(List<String> args, Consumer<String> out, Consumer<String> err) {
        if (args.size() < 2) {
            err.accept("configureFromJson requires 1 argument [jsonConfig]");
            return;
        }

        // Reconstruct the JSON string (in case it has spaces)
        String json = String.join(" ", args.subList(1, args.size()));

        try {
            MarketDataBookConfig config = objectMapper.readValue(json, MarketDataBookConfig.class);

            // Set feed and venue names if not provided
            if (config.getFeedName() == null || config.getFeedName().isEmpty()) {
                config.setFeedName(getFeedName());
            }
            if (config.getVenueName() == null || config.getVenueName().isEmpty()) {
                config.setVenueName(getFeedName());
            }

            // Remove existing config if present
            marketDataBookConfigs.removeIf(c -> c.getSymbol().equals(config.getSymbol()));
            marketDataBookConfigs.add(config);
            configMap.put(config.getSymbol(), config);

            out.accept(String.format("Configured symbol %s from JSON", config.getSymbol()));
            log.info("Configured symbol {} from JSON: {}", config.getSymbol(), json);
        } catch (Exception e) {
            err.accept("Failed to parse JSON configuration: " + e.getMessage());
            log.error("Failed to parse JSON configuration", e);
        }
    }

    protected void clearAll(List<String> args, Consumer<String> out, Consumer<String> err) {
        int count = marketDataBookConfigs.size();
        marketDataBookConfigs.clear();
        configMap.clear();
        out.accept(String.format("Cleared all %d market data book configurations", count));
        log.info("Cleared all market data book configurations");
    }
}
