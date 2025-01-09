/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.cache;

import lombok.Getter;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class InMemoryCache implements Cache {

    private final Map<String, Object> cache = new ConcurrentHashMap<>();

    @Override
    public Collection<String> keys() {
        return cache.keySet();
    }

    @Override
    public void put(String key, Object value) {
        cache.put(key, value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) cache.get(key);
    }

    @Override
    public void remove(String key) {
        cache.remove(key);
    }
}
