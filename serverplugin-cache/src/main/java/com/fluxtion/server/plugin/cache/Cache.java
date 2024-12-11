package com.fluxtion.server.plugin.cache;

public interface Cache {

    void put(String key, Object value);

    <T> T get(String key);

    default <T> T getOrDefault(String key, T defaultValue) {
        T value = get(key);
        return value == null ? defaultValue : value;
    }

    void remove(String key);
}
