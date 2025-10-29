/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.trading.service.marketdata;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MultilevelBookConfig configuration object.
 */
class MultilevelBookConfigTest {

    @Test
    void testDefaultConfig() {
        MultilevelBookConfig config = MultilevelBookConfig.defaultConfig();

        assertEquals(20, config.getMaxDepth());
        assertTrue(config.isTrimEmptyLevels());
        assertTrue(config.isValidateLevels());
    }

    @Test
    void testShallowConfig() {
        MultilevelBookConfig config = MultilevelBookConfig.shallow();

        assertEquals(10, config.getMaxDepth());
    }

    @Test
    void testDeepConfig() {
        MultilevelBookConfig config = MultilevelBookConfig.deep();

        assertEquals(50, config.getMaxDepth());
    }

    @Test
    void testHighPerformanceConfig() {
        MultilevelBookConfig config = MultilevelBookConfig.highPerformance();

        assertEquals(20, config.getMaxDepth());
        assertFalse(config.isValidateLevels());  // Validation disabled for performance
    }

    @Test
    void testCustomConfig() {
        MultilevelBookConfig config = MultilevelBookConfig.builder()
                .maxDepth(15)
                .validateLevels(false)
                .trimEmptyLevels(false)
                .build();

        assertEquals(15, config.getMaxDepth());
        assertFalse(config.isValidateLevels());
        assertFalse(config.isTrimEmptyLevels());
    }

    @Test
    void testValidation_ValidConfig() {
        MultilevelBookConfig config = MultilevelBookConfig.builder()
                .maxDepth(10)
                .build();

        assertDoesNotThrow(config::validate);
    }

    @Test
    void testValidation_TooSmall() {
        MultilevelBookConfig config = MultilevelBookConfig.builder()
                .maxDepth(0)
                .build();

        assertThrows(IllegalArgumentException.class, config::validate);
    }

    @Test
    void testValidation_TooLarge() {
        MultilevelBookConfig config = MultilevelBookConfig.builder()
                .maxDepth(1001)
                .build();

        assertThrows(IllegalArgumentException.class, config::validate);
    }

    @Test
    void testEquality() {
        MultilevelBookConfig config1 = MultilevelBookConfig.builder()
                .maxDepth(20)
                .build();
        MultilevelBookConfig config2 = MultilevelBookConfig.builder()
                .maxDepth(20)
                .build();

        assertEquals(config1, config2);
    }
}
