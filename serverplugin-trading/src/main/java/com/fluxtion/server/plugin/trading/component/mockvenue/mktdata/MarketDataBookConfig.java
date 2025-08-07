package com.fluxtion.server.plugin.trading.component.mockvenue.mktdata;

import lombok.Data;

@Data
public class MarketDataBookConfig {
    private double minPrice;
    private double maxPrice;
    private double minSpread;
    private double maxSpread;
    private double minVolume;
    private double maxVolume;
    private String feedName;
    private String venueName;
    private String symbol;
    private double publishProbability;
    private int pricePrecision = 3;
    private int quantityPrecision = 3;

}
