/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.cache;

import java.util.Collection;

public interface Cache {

    Collection<String> keys();

    void put(String key, Object value);

    <T> T get(String key);

    default <T> T getOrDefault(String key, T defaultValue) {
        T value = get(key);
        return value == null ? defaultValue : value;
    }

    void remove(String key);
}
