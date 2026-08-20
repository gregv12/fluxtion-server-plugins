package com.fluxtion.server.plugin.cache;

/**
 * Capability interface for a {@link Cache} that exposes a monotonic version bumped on every mutation,
 * so a reader on another thread can cheaply detect that a shared cache changed since a prior
 * observation — no content polling, no writer-published events. A reader rebuilds an immutable
 * snapshot only when {@link #version()} moves.
 *
 * <p>Modelled as a mix-in (like {@code Lifecycle}/{@code Agent}) rather than a method on {@code Cache}
 * so caches that don't version don't have to fake one; consumers detect support with
 * {@code cache instanceof VersionedCache}.
 *
 * <p><b>Contract:</b> cached values are replaced, never mutated in place — a reader snapshots
 * references on version change, so an in-place mutation would be seen through the snapshot and void
 * the guarantee. Memory ordering: implementations bump the version <em>after</em> the write is
 * visible, so observing version {@code v} guarantees observing all writes that preceded that bump.
 */
public interface VersionedCache extends Cache {

    /** Monotonic, incremented on every {@code put}/{@code remove}. Safe to read from any thread. */
    long version();
}
