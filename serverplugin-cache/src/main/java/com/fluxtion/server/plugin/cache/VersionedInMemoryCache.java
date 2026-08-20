package com.fluxtion.server.plugin.cache;

import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link InMemoryCache} that versions every mutation ({@link VersionedCache}). Register it under a
 * cache name shared by a writer and a reader so the writer's put/remove bump the version the reader
 * checks. Not file-backed — see {@link VersionedJsonFileCache} for a persistent variant.
 */
public class VersionedInMemoryCache extends InMemoryCache implements VersionedCache {

    private final AtomicLong version = new AtomicLong();

    @Override
    public void put(String key, Object value) {
        super.put(key, value);          // write first…
        version.incrementAndGet();      // …then bump (volatile write publishes the put)
    }

    @Override
    public void remove(String key) {
        super.remove(key);
        version.incrementAndGet();
    }

    @Override
    public long version() {
        return version.get();
    }
}
