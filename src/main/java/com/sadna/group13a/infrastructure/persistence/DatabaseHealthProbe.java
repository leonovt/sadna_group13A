package com.sadna.group13a.infrastructure.persistence;

/**
 * Abstraction over the data store's reachability — the equivalent of HikariCP's
 * connection-test query for this project's in-memory persistence (issue #228).
 *
 * <p>The default implementation always reports reachable; it is the seam through
 * which a connection outage can be simulated and recovered from.
 */
@FunctionalInterface
public interface DatabaseHealthProbe {

    /** @return {@code true} if the data store is currently reachable. */
    boolean isReachable();
}
