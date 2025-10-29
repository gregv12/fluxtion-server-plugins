package com.fluxtion.server.plugin.trading.service.node.making;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VenueOrderTransformerTest {

    @Test
    void transformPrice_withinValidRange_returnsRoundedPrice() {
        MakingVenueConfig config = new MakingVenueConfig();
        config.setMinPrice(1.0);
        config.setMaxPrice(1000.0);
        config.setPrecisionDpsPrice(2);

        double result = VenueOrderTransformer.transformPrice(123.456, config);
        assertEquals(123.46, result, 0.001);
    }

    @Test
    void transformPrice_belowMinPrice_returnsNaN() {
        MakingVenueConfig config = new MakingVenueConfig();
        config.setMinPrice(10.0);
        config.setMaxPrice(1000.0);
        config.setPrecisionDpsPrice(2);

        double result = VenueOrderTransformer.transformPrice(5.0, config);
        assertTrue(Double.isNaN(result));
    }

    @Test
    void transformPrice_aboveMaxPrice_returnsNaN() {
        MakingVenueConfig config = new MakingVenueConfig();
        config.setMinPrice(1.0);
        config.setMaxPrice(100.0);
        config.setPrecisionDpsPrice(2);

        double result = VenueOrderTransformer.transformPrice(150.0, config);
        assertTrue(Double.isNaN(result));
    }

    @Test
    void transformPrice_withNaN_returnsNaN() {
        MakingVenueConfig config = new MakingVenueConfig();
        config.setMinPrice(1.0);
        config.setMaxPrice(1000.0);
        config.setPrecisionDpsPrice(2);

        double result = VenueOrderTransformer.transformPrice(Double.NaN, config);
        assertTrue(Double.isNaN(result));
    }

    @Test
    void transformPrice_withDifferentPrecision_roundsCorrectly() {
        MakingVenueConfig config = new MakingVenueConfig();
        config.setMinPrice(1.0);
        config.setMaxPrice(1000.0);
        config.setPrecisionDpsPrice(4);

        double result = VenueOrderTransformer.transformPrice(123.456789, config);
        assertEquals(123.4568, result, 0.00001);
    }

    @Test
    void transformPrice_atExactMinPrice_returnsValue() {
        MakingVenueConfig config = new MakingVenueConfig();
        config.setMinPrice(10.0);
        config.setMaxPrice(1000.0);
        config.setPrecisionDpsPrice(2);

        double result = VenueOrderTransformer.transformPrice(10.0, config);
        assertEquals(10.0, result, 0.001);
    }

    @Test
    void transformPrice_atExactMaxPrice_returnsValue() {
        MakingVenueConfig config = new MakingVenueConfig();
        config.setMinPrice(1.0);
        config.setMaxPrice(100.0);
        config.setPrecisionDpsPrice(2);

        double result = VenueOrderTransformer.transformPrice(100.0, config);
        assertEquals(100.0, result, 0.001);
    }

    @Test
    void transformQuantity_withinValidRange_returnsRoundedQuantity() {
        MakingVenueConfig config = new MakingVenueConfig();
        config.setMinQuantity(0.1);
        config.setMaxQuantity(1000.0);
        config.setPrecisionDpsQuantity(2);

        double result = VenueOrderTransformer.transformQuantity(25.678, config);
        assertEquals(25.68, result, 0.001);
    }

    @Test
    void transformQuantity_belowMinQuantity_returnsZero() {
        MakingVenueConfig config = new MakingVenueConfig();
        config.setMinQuantity(1.0);
        config.setMaxQuantity(1000.0);
        config.setPrecisionDpsQuantity(2);

        double result = VenueOrderTransformer.transformQuantity(0.5, config);
        assertEquals(0.0, result, 0.001);
    }

    @Test
    void transformQuantity_aboveMaxQuantity_returnsMaxQuantity() {
        MakingVenueConfig config = new MakingVenueConfig();
        config.setMinQuantity(0.1);
        config.setMaxQuantity(100.0);
        config.setPrecisionDpsQuantity(2);

        double result = VenueOrderTransformer.transformQuantity(150.0, config);
        assertEquals(100.0, result, 0.001);
    }

    @Test
    void transformQuantity_withNaN_returnsNaN() {
        MakingVenueConfig config = new MakingVenueConfig();
        config.setMinQuantity(0.1);
        config.setMaxQuantity(1000.0);
        config.setPrecisionDpsQuantity(2);

        double result = VenueOrderTransformer.transformQuantity(Double.NaN, config);
        assertTrue(Double.isNaN(result));
    }

    @Test
    void transformQuantity_withDifferentPrecision_roundsCorrectly() {
        MakingVenueConfig config = new MakingVenueConfig();
        config.setMinQuantity(0.001);
        config.setMaxQuantity(1000.0);
        config.setPrecisionDpsQuantity(4);

        double result = VenueOrderTransformer.transformQuantity(12.345678, config);
        assertEquals(12.3457, result, 0.00001);
    }

    @Test
    void transformQuantity_atExactMinQuantity_returnsValue() {
        MakingVenueConfig config = new MakingVenueConfig();
        config.setMinQuantity(1.0);
        config.setMaxQuantity(1000.0);
        config.setPrecisionDpsQuantity(2);

        double result = VenueOrderTransformer.transformQuantity(1.0, config);
        assertEquals(1.0, result, 0.001);
    }

    @Test
    void transformQuantity_atExactMaxQuantity_returnsValue() {
        MakingVenueConfig config = new MakingVenueConfig();
        config.setMinQuantity(0.1);
        config.setMaxQuantity(100.0);
        config.setPrecisionDpsQuantity(2);

        double result = VenueOrderTransformer.transformQuantity(100.0, config);
        assertEquals(100.0, result, 0.001);
    }

    @Test
    void transformQuantity_slightlyBelowMin_returnsZero() {
        MakingVenueConfig config = new MakingVenueConfig();
        config.setMinQuantity(1.0);
        config.setMaxQuantity(1000.0);
        config.setPrecisionDpsQuantity(2);

        double result = VenueOrderTransformer.transformQuantity(0.999, config);
        assertEquals(0.0, result, 0.001);
    }

    @Test
    void transformQuantity_slightlyAboveMax_clampedToMax() {
        MakingVenueConfig config = new MakingVenueConfig();
        config.setMinQuantity(0.1);
        config.setMaxQuantity(100.0);
        config.setPrecisionDpsQuantity(2);

        double result = VenueOrderTransformer.transformQuantity(100.001, config);
        assertEquals(100.0, result, 0.001);
    }

    @Test
    void transformPrice_zeroPrecision_noDecimalPlaces() {
        MakingVenueConfig config = new MakingVenueConfig();
        config.setMinPrice(1.0);
        config.setMaxPrice(1000.0);
        config.setPrecisionDpsPrice(0);

        double result = VenueOrderTransformer.transformPrice(123.789, config);
        assertEquals(124.0, result, 0.001);
    }

    @Test
    void transformQuantity_zeroPrecision_noDecimalPlaces() {
        MakingVenueConfig config = new MakingVenueConfig();
        config.setMinQuantity(1.0);
        config.setMaxQuantity(1000.0);
        config.setPrecisionDpsQuantity(0);

        double result = VenueOrderTransformer.transformQuantity(25.789, config);
        assertEquals(26.0, result, 0.001);
    }
}
