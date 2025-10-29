package com.fluxtion.server.plugin.trading.service.node.making;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MakingVenueConfigTest {

    @Test
    void configWithAllProperties_setsAndGetsCorrectly() {
        MakingVenueConfig config = new MakingVenueConfig();

        config.setFeedName("testFeed");
        config.setVenueName("testVenue");
        config.setBook("testBook");
        config.setSymbol("BTCUSD");

        config.setMinQuantity(0.001);
        config.setMaxQuantity(1000.0);
        config.setPrecisionDpsQuantity(3);
        config.setStepSizeQuantity(1);

        config.setMinPrice(0.01);
        config.setMaxPrice(100000.0);
        config.setPrecisionDpsPrice(2);
        config.setStepSizePrice(1);

        assertEquals("testFeed", config.getFeedName());
        assertEquals("testVenue", config.getVenueName());
        assertEquals("testBook", config.getBook());
        assertEquals("BTCUSD", config.getSymbol());

        assertEquals(0.001, config.getMinQuantity());
        assertEquals(1000.0, config.getMaxQuantity());
        assertEquals(3, config.getPrecisionDpsQuantity());
        assertEquals(1, config.getStepSizeQuantity());

        assertEquals(0.01, config.getMinPrice());
        assertEquals(100000.0, config.getMaxPrice());
        assertEquals(2, config.getPrecisionDpsPrice());
        assertEquals(1, config.getStepSizePrice());
    }

    @Test
    void configWithDefaultValues_hasReasonableDefaults() {
        MakingVenueConfig config = new MakingVenueConfig();

        // Check default min price
        assertEquals(0.000001, config.getMinPrice());
    }

    @Test
    void configCanBeModified_afterCreation() {
        MakingVenueConfig config = new MakingVenueConfig();

        config.setSymbol("ETHUSD");
        assertEquals("ETHUSD", config.getSymbol());

        config.setSymbol("BTCUSD");
        assertEquals("BTCUSD", config.getSymbol());
    }

    @Test
    void configSupportsNullValues() {
        MakingVenueConfig config = new MakingVenueConfig();

        config.setFeedName(null);
        config.setVenueName(null);
        config.setBook(null);
        config.setSymbol(null);

        assertNull(config.getFeedName());
        assertNull(config.getVenueName());
        assertNull(config.getBook());
        assertNull(config.getSymbol());
    }

    @Test
    void configToString_containsAllFields() {
        MakingVenueConfig config = new MakingVenueConfig();
        config.setFeedName("testFeed");
        config.setVenueName("testVenue");
        config.setSymbol("BTCUSD");

        String toString = config.toString();
        assertNotNull(toString);
        assertTrue(toString.contains("testFeed") || toString.contains("feedName"));
        assertTrue(toString.contains("testVenue") || toString.contains("venueName"));
        assertTrue(toString.contains("BTCUSD") || toString.contains("symbol"));
    }

    @Test
    void configEquals_comparesCorrectly() {
        MakingVenueConfig config1 = new MakingVenueConfig();
        config1.setSymbol("BTCUSD");
        config1.setVenueName("venue1");
        config1.setMinPrice(1.0);
        config1.setMaxPrice(1000.0);

        MakingVenueConfig config2 = new MakingVenueConfig();
        config2.setSymbol("BTCUSD");
        config2.setVenueName("venue1");
        config2.setMinPrice(1.0);
        config2.setMaxPrice(1000.0);

        assertEquals(config1, config2);
        assertEquals(config1.hashCode(), config2.hashCode());
    }

    @Test
    void configEquals_detectsDifferences() {
        MakingVenueConfig config1 = new MakingVenueConfig();
        config1.setSymbol("BTCUSD");
        config1.setMinPrice(1.0);

        MakingVenueConfig config2 = new MakingVenueConfig();
        config2.setSymbol("ETHUSD");
        config2.setMinPrice(1.0);

        assertNotEquals(config1, config2);
    }

    @Test
    void configWithZeroValues_handlesCorrectly() {
        MakingVenueConfig config = new MakingVenueConfig();

        config.setMinQuantity(0.0);
        config.setMaxQuantity(0.0);
        config.setMinPrice(0.0);
        config.setMaxPrice(0.0);

        assertEquals(0.0, config.getMinQuantity());
        assertEquals(0.0, config.getMaxQuantity());
        assertEquals(0.0, config.getMinPrice());
        assertEquals(0.0, config.getMaxPrice());
    }

    @Test
    void configWithNegativePrecision_acceptsValue() {
        MakingVenueConfig config = new MakingVenueConfig();

        config.setPrecisionDpsPrice(-1);
        config.setPrecisionDpsQuantity(-1);

        assertEquals(-1, config.getPrecisionDpsPrice());
        assertEquals(-1, config.getPrecisionDpsQuantity());
    }

    @Test
    void configWithLargeValues_handlesCorrectly() {
        MakingVenueConfig config = new MakingVenueConfig();

        config.setMaxQuantity(Double.MAX_VALUE);
        config.setMaxPrice(Double.MAX_VALUE);

        assertEquals(Double.MAX_VALUE, config.getMaxQuantity());
        assertEquals(Double.MAX_VALUE, config.getMaxPrice());
    }

    @Test
    void configForCryptocurrency_realisticValues() {
        MakingVenueConfig config = new MakingVenueConfig();

        // Typical crypto exchange configuration
        config.setFeedName("binance");
        config.setVenueName("binance");
        config.setBook("spot");
        config.setSymbol("BTCUSDT");

        config.setMinQuantity(0.00001);  // 0.00001 BTC
        config.setMaxQuantity(9000.0);   // Max 9000 BTC per order
        config.setPrecisionDpsQuantity(5);

        config.setMinPrice(0.01);        // Min $0.01
        config.setMaxPrice(1000000.0);   // Max $1M
        config.setPrecisionDpsPrice(2);

        assertNotNull(config);
        assertEquals("BTCUSDT", config.getSymbol());
        assertEquals(5, config.getPrecisionDpsQuantity());
        assertEquals(2, config.getPrecisionDpsPrice());
    }

    @Test
    void configForForex_realisticValues() {
        MakingVenueConfig config = new MakingVenueConfig();

        // Typical forex configuration
        config.setFeedName("fxcm");
        config.setVenueName("fxcm");
        config.setBook("fx");
        config.setSymbol("EURUSD");

        config.setMinQuantity(1000.0);    // Min 1000 units
        config.setMaxQuantity(10000000.0); // Max 10M units
        config.setPrecisionDpsQuantity(0);

        config.setMinPrice(0.0001);       // Typical forex pip
        config.setMaxPrice(10.0);
        config.setPrecisionDpsPrice(5);   // 5 decimal places for forex

        assertNotNull(config);
        assertEquals("EURUSD", config.getSymbol());
        assertEquals(0, config.getPrecisionDpsQuantity());
        assertEquals(5, config.getPrecisionDpsPrice());
    }
}
