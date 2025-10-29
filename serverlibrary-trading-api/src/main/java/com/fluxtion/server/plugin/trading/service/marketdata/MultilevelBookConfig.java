/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.trading.service.marketdata;

import lombok.Builder;
import lombok.Value;

/**
 * Configuration for multilevel market data book behavior.
 *
 * <p>This configuration controls the capacity and behavior of order books,
 * optimized for market making systems that require deep liquidity visibility.</p>
 *
 * @see MultilevelMarketDataBook
 */
@Value
@Builder
public class MultilevelBookConfig {

    /**
     * Maximum depth per side (bid/ask).
     * <p>Default: 20 levels - optimized for market making systems</p>
     */
    @Builder.Default
    int maxDepth = 20;

    /**
     * Whether to trim empty levels automatically when they reach zero quantity.
     * <p>Default: true - maintains clean book state</p>
     */
    @Builder.Default
    boolean trimEmptyLevels = true;

    /**
     * Whether to validate price levels on updates (checks for positive prices,
     * non-negative quantities).
     * <p>Default: true - disable for maximum performance in production</p>
     */
    @Builder.Default
    boolean validateLevels = true;

    /**
     * Default configuration for market making (20 levels, validation enabled).
     *
     * @return configuration with 20 levels per side
     */
    public static MultilevelBookConfig defaultConfig() {
        return MultilevelBookConfig.builder().build();
    }

    /**
     * Configuration for shallow books (10 levels per side).
     *
     * @return configuration with 10 levels per side
     */
    public static MultilevelBookConfig shallow() {
        return MultilevelBookConfig.builder()
                .maxDepth(10)
                .build();
    }

    /**
     * Configuration for deep books (50 levels per side).
     *
     * @return configuration with 50 levels per side
     */
    public static MultilevelBookConfig deep() {
        return MultilevelBookConfig.builder()
                .maxDepth(50)
                .build();
    }

    /**
     * High-performance configuration (20 levels, validation disabled).
     * Use this in production after thorough testing.
     *
     * @return configuration optimized for performance
     */
    public static MultilevelBookConfig highPerformance() {
        return MultilevelBookConfig.builder()
                .maxDepth(20)
                .validateLevels(false)
                .build();
    }

    /**
     * Validates this configuration.
     *
     * @throws IllegalArgumentException if maxDepth is invalid
     */
    public void validate() {
        if (maxDepth < 1) {
            throw new IllegalArgumentException("maxDepth must be at least 1, got: " + maxDepth);
        }
        if (maxDepth > 1000) {
            throw new IllegalArgumentException("maxDepth too large (> 1000), got: " + maxDepth);
        }
    }
}
