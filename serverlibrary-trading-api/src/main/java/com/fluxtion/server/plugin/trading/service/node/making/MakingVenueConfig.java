package com.fluxtion.server.plugin.trading.service.node.making;

import lombok.Data;

/**
 * MakingVenueConfig defines symbol, pricing, and quantity constraints for a specific
 * trading venue. It is used by MakingOrderNode and helpers to validate and transform
 * order parameters before submission or modification.
 */
@Data
public class MakingVenueConfig {
    //symbol information
    private String feedName;
    private String venueName;
    private String book;
    private String symbol;

    //quantity rules
    private double minQuantity;
    private double maxQuantity;
    private int precisionDpsQuantity;
    private int stepSizeQuantity;

    //pricing rules
    private double minPrice = 0.000001;
    private double maxPrice;
    private int precisionDpsPrice;
    private int stepSizePrice;

}
