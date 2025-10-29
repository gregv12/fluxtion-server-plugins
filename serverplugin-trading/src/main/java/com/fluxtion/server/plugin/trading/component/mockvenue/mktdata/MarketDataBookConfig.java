package com.fluxtion.server.plugin.trading.component.mockvenue.mktdata;

import com.fluxtion.server.plugin.trading.service.marketdata.MultilevelBookConfig;
import lombok.Data;

/**
 * Configuration class for simulated market data book generation.
 * Defines parameters for controlling price, spread, volume ranges, and publishing behavior
 * of both single-level and multi-level market data books.
 */
@Data
public class MarketDataBookConfig {
    /**
     * Minimum price level for market data generation
     */
    private double minPrice;
    /**
     * Maximum price level for market data generation
     */
    private double maxPrice;
    /**
     * Minimum spread between bid and ask prices
     */
    private double minSpread;
    /**
     * Maximum spread between bid and ask prices
     */
    private double maxSpread;
    /**
     * Minimum volume for orders
     */
    private double minVolume;
    /**
     * Maximum volume for orders
     */
    private double maxVolume;
    /**
     * Name of the market data feed
     */
    private String feedName;
    /**
     * Name of the trading venue
     */
    private String venueName;
    /**
     * Trading instrument symbol
     */
    private String symbol;
    /**
     * Probability of publishing market data updates (0.0 to 1.0)
     */
    private double publishProbability;
    /**
     * Decimal places precision for price values
     */
    private int precisionDpsPrice = 3;
    /**
     * Decimal places precision for volume values
     */
    private int precisionDpsVolume = 3;

    /**
     * Flag to enable multi-level order book generation
     */
    private boolean multilevel = false;
    /**
     * Number of price levels in the multi-level order book
     */
    private int multilevelDepth = 20;
    /**
     * Configuration for multi-level order book behavior
     */
    private MultilevelBookConfig multilevelBookConfig = MultilevelBookConfig.defaultConfig();

}
