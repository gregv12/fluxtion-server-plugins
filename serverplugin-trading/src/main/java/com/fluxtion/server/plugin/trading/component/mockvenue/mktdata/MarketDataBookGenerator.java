package com.fluxtion.server.plugin.trading.component.mockvenue.mktdata;

import com.fluxtion.server.plugin.trading.service.marketdata.MarketDataBook;
import com.fluxtion.server.plugin.trading.service.marketdata.MultilevelBookConfig;
import com.fluxtion.server.plugin.trading.service.marketdata.MultilevelMarketDataBook;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

public class MarketDataBookGenerator {
    private static final Random RANDOM = new Random();
    private static final AtomicLong ID_GENERATOR = new AtomicLong(0);

    public static MarketDataBook generateRandom(MarketDataBookConfig config) {
        // Check publish probability first
        if (RANDOM.nextDouble() >= config.getPublishProbability()) {
            return null;
        }
        
        // Generate random mid price within the configured range
        double midPrice = roundToDecimalPlaces(
            config.getMinPrice() + RANDOM.nextDouble() * (config.getMaxPrice() - config.getMinPrice()),
            config.getPrecisionDpsPrice()
        );
        
        // Generate random spread within the configured range
        double spread = roundToDecimalPlaces(
            config.getMinSpread() + RANDOM.nextDouble() * (config.getMaxSpread() - config.getMinSpread()),
            config.getPrecisionDpsVolume()
        );
        
        // Calculate bid and ask prices
        double halfSpread = spread / 2.0;
        double bidPrice = roundToDecimalPlaces(midPrice - halfSpread, config.getPrecisionDpsPrice());
        double askPrice = roundToDecimalPlaces(midPrice + halfSpread, config.getPrecisionDpsPrice());
        
        // Generate random volumes and round them
        double bidVolume = roundToDecimalPlaces(
            config.getMinVolume() + RANDOM.nextDouble() * (config.getMaxVolume() - config.getMinVolume()),
            config.getPrecisionDpsVolume()
        );
        double askVolume = roundToDecimalPlaces(
            config.getMinVolume() + RANDOM.nextDouble() * (config.getMaxVolume() - config.getMinVolume()),
            config.getPrecisionDpsVolume()
        );

        return new MarketDataBook(
            config.getFeedName(),
            config.getVenueName(),
            config.getSymbol(),
            ID_GENERATOR.incrementAndGet(),
            bidPrice,
            bidVolume,
            askPrice,
            askVolume
        );
    }

    /**
     * Generates a random multilevel market data book with realistic price levels.
     *
     * <p>Generates a book with decreasing liquidity away from the best price,
     * simulating realistic market depth characteristics for market making systems.</p>
     *
     * @param config the market data book configuration
     * @param depth the number of levels to generate per side (must be <= bookConfig max depth)
     * @param bookConfig the multilevel book configuration
     * @return a multilevel book with randomly generated price levels, or null based on publish probability
     */
    public static MultilevelMarketDataBook generateRandomMultilevel(
            MarketDataBookConfig config,
            int depth,
            MultilevelBookConfig bookConfig) {

        // Check publish probability first
        if (RANDOM.nextDouble() >= config.getPublishProbability()) {
            return null;
        }

        // Validate depth
        if (depth > bookConfig.getMaxDepth()) {
            throw new IllegalArgumentException(
                    "Requested depth " + depth + " exceeds book capacity " + bookConfig.getMaxDepth());
        }

        // Generate random mid price
        double midPrice = roundToDecimalPlaces(
                config.getMinPrice() + RANDOM.nextDouble() * (config.getMaxPrice() - config.getMinPrice()),
                config.getPrecisionDpsPrice()
        );

        // Generate base spread
        double baseSpread = roundToDecimalPlaces(
                config.getMinSpread() + RANDOM.nextDouble() * (config.getMaxSpread() - config.getMinSpread()),
                config.getPrecisionDpsPrice()
        );

        // Create the multilevel book
        MultilevelMarketDataBook book = new MultilevelMarketDataBook(
                config.getFeedName(),
                config.getVenueName(),
                config.getSymbol(),
                ID_GENERATOR.incrementAndGet(),
                bookConfig
        );

        // Generate levels with decreasing liquidity
        double tickSize = baseSpread;  // Use spread as tick size
        for (int i = 0; i < depth; i++) {
            // Calculate prices (bids descending, asks ascending)
            double bidPrice = roundToDecimalPlaces(
                    midPrice - (baseSpread / 2.0) - (i * tickSize),
                    config.getPrecisionDpsPrice()
            );
            double askPrice = roundToDecimalPlaces(
                    midPrice + (baseSpread / 2.0) + (i * tickSize),
                    config.getPrecisionDpsPrice()
            );

            // Generate volumes with decreasing liquidity (more at top)
            double liquidityFactor = 1.0 / (i + 1);  // Decreases: 1.0, 0.5, 0.33, 0.25...
            double baseVolume = roundToDecimalPlaces(
                    config.getMinVolume() + RANDOM.nextDouble() * (config.getMaxVolume() - config.getMinVolume()),
                    config.getPrecisionDpsVolume()
            );

            double bidVolume = roundToDecimalPlaces(
                    baseVolume * liquidityFactor,
                    config.getPrecisionDpsVolume()
            );
            double askVolume = roundToDecimalPlaces(
                    baseVolume * liquidityFactor,
                    config.getPrecisionDpsVolume()
            );

            // Generate order counts (decreasing with levels)
            int orderCount = Math.max(1, (int) (10.0 * liquidityFactor));

            // Add levels to book
            book.updateBid(bidPrice, bidVolume, orderCount);
            book.updateAsk(askPrice, askVolume, orderCount);
        }

        return book;
    }

    /**
     * Generates a random multilevel market data book with default configuration (20 levels).
     *
     * @param config the market data book configuration
     * @return a multilevel book with 20 levels per side, or null based on publish probability
     */
    public static MultilevelMarketDataBook generateRandomMultilevel(MarketDataBookConfig config) {
        return generateRandomMultilevel(config, 20, MultilevelBookConfig.defaultConfig());
    }

    /**
     * Generates a random multilevel market data book with specified depth.
     *
     * @param config the market data book configuration
     * @param depth the number of levels to generate per side
     * @return a multilevel book with specified depth, or null based on publish probability
     */
    public static MultilevelMarketDataBook generateRandomMultilevel(
            MarketDataBookConfig config,
            int depth) {

        // Create book config with appropriate max depth
        MultilevelBookConfig bookConfig = MultilevelBookConfig.builder()
                .maxDepth(Math.max(depth, 20))  // At least depth, or 20
                .build();

        return generateRandomMultilevel(config, depth, bookConfig);
    }

    private static double roundToDecimalPlaces(double value, int places) {
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }
}
