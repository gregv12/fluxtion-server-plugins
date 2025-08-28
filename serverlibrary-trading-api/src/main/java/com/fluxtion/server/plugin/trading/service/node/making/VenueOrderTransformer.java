package com.fluxtion.server.plugin.trading.service.node.making;

import com.fluxtion.server.plugin.trading.service.node.util.MathUtil;

/**
 * VenueOrderTransformer provides helper methods to normalise price and quantity
 * values according to MakingVenueConfig limits and precision settings.
 */
public class VenueOrderTransformer {

    public static double transformPrice(double price, MakingVenueConfig makingVenueConfig) {
        price = price < makingVenueConfig.getMinPrice() ? Double.NaN : price;
        price = price > makingVenueConfig.getMaxPrice() ? Double.NaN : price;

        if (Double.isNaN(price)) {
            return price;
        }

        price = MathUtil.round(price, makingVenueConfig.getPrecisionDpsPrice());
        return price;
    }

    public static double transformQuantity(double quantity, MakingVenueConfig makingVenueConfig) {
        if (Double.isNaN(quantity)) {
            return quantity;
        }

        quantity = quantity < makingVenueConfig.getMinQuantity() ? 0 : quantity;
        quantity = Math.min(makingVenueConfig.getMaxQuantity(), quantity);
        quantity = MathUtil.round(quantity, makingVenueConfig.getPrecisionDpsQuantity());
        return quantity;
    }
}
