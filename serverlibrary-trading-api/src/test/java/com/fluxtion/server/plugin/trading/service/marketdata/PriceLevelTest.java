/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.trading.service.marketdata;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PriceLevel immutable value object.
 */
class PriceLevelTest {

    @Test
    void testConstruction() {
        PriceLevel level = new PriceLevel(100.0, 1.5, 3);

        assertEquals(100.0, level.getPrice(), 0.0001);
        assertEquals(1.5, level.getQuantity(), 0.0001);
        assertEquals(3, level.getOrderCount());
    }

    @Test
    void testEmptyLevel() {
        PriceLevel level = PriceLevel.empty(100.0);

        assertEquals(100.0, level.getPrice(), 0.0001);
        assertEquals(0.0, level.getQuantity(), 0.0001);
        assertEquals(0, level.getOrderCount());
        assertTrue(level.isEmpty());
    }

    @Test
    void testIsEmpty() {
        assertTrue(new PriceLevel(100.0, 0.0, 0).isEmpty());
        assertTrue(new PriceLevel(100.0, -0.1, 0).isEmpty());
        assertFalse(new PriceLevel(100.0, 0.1, 0).isEmpty());
    }

    @Test
    void testIsValid() {
        assertTrue(new PriceLevel(100.0, 1.0, 1).isValid());
        assertTrue(new PriceLevel(100.0, 0.0, 0).isValid());  // zero quantity is valid
        assertFalse(new PriceLevel(0.0, 1.0, 1).isValid());   // zero price invalid
        assertFalse(new PriceLevel(-1.0, 1.0, 1).isValid());  // negative price invalid
        assertFalse(new PriceLevel(100.0, -1.0, 1).isValid()); // negative quantity invalid
        assertFalse(new PriceLevel(100.0, 1.0, -1).isValid()); // negative order count invalid
    }

    @Test
    void testImmutability_WithMethods() {
        PriceLevel original = new PriceLevel(100.0, 1.0, 1);
        PriceLevel modified = original.withQuantity(2.0);

        assertEquals(1.0, original.getQuantity(), 0.0001);  // unchanged
        assertEquals(2.0, modified.getQuantity(), 0.0001);  // new instance
        assertNotSame(original, modified);
    }

    @Test
    void testEquals() {
        PriceLevel level1 = new PriceLevel(100.0, 1.0, 1);
        PriceLevel level2 = new PriceLevel(100.0, 1.0, 1);
        PriceLevel level3 = new PriceLevel(100.0, 2.0, 1);

        assertEquals(level1, level2);
        assertNotEquals(level1, level3);
    }

    @Test
    void testHashCode() {
        PriceLevel level1 = new PriceLevel(100.0, 1.0, 1);
        PriceLevel level2 = new PriceLevel(100.0, 1.0, 1);

        assertEquals(level1.hashCode(), level2.hashCode());
    }

    @Test
    void testToString() {
        PriceLevel level = new PriceLevel(100.123, 1.456, 3);
        String str = level.toString();

        assertTrue(str.contains("100.123"));
        assertTrue(str.contains("1.456"));
        assertTrue(str.contains("3"));
    }
}
