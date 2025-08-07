package com.fluxtion.server.plugin.trading.component.mockvenue.mktdata;

import com.fluxtion.server.plugin.trading.service.marketdata.MarketDataBook;
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

    private static double roundToDecimalPlaces(double value, int places) {
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }
}
