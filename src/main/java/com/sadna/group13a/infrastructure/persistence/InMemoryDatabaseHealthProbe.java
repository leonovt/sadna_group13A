package com.sadna.group13a.infrastructure.persistence;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Default {@link DatabaseHealthProbe} for the in-memory data store, which is always
 * reachable in normal operation. Exposes {@link #simulateOutage()} / {@link #simulateRestore()}
 * so a connection-loss → reconnection cycle can be exercised (issue #228) without a real DB.
 */
@Component
public class InMemoryDatabaseHealthProbe implements DatabaseHealthProbe {

    private final AtomicBoolean reachable = new AtomicBoolean(true);

    @Override
    public boolean isReachable() {
        return reachable.get();
    }

    /** Simulates a lost connection to the data store. */
    public void simulateOutage() {
        reachable.set(false);
    }

    /** Simulates the data store becoming reachable again. */
    public void simulateRestore() {
        reachable.set(true);
    }
}
