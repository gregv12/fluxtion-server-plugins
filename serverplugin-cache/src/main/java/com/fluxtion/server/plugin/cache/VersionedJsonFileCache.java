package com.fluxtion.server.plugin.cache;

import java.util.concurrent.atomic.AtomicLong;

/**
 * File-backed {@link JsonFileCache} that also versions every runtime mutation ({@link VersionedCache})
 * — so it can be a shared cache where one component persists/writes and another needs change-detection
 * for a version-gated snapshot.
 *
 * <p>The parent loads the file straight into its map at init (not via {@link #put}), so on startup the
 * version is {@code 0} and a reader builds its snapshot once from the loaded entries; the version only
 * advances on runtime {@code put}/{@code remove}, which is exactly when a reader must rebuild.
 */
public class VersionedJsonFileCache extends JsonFileCache implements VersionedCache {

    private final AtomicLong version = new AtomicLong();

    @Override
    public void put(String key, Object value) {
        super.put(key, value);
        version.incrementAndGet();
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
