package com.sadna.group13a.infrastructure.persistence;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-memory {@link DatabaseHealthProbe} test seam. The running application uses the
 * {@code @Primary} {@link DataSourceHealthProbe}, which checks the real JDBC datasource;
 * this probe exposes {@link #simulateOutage()} / {@link #simulateRestore()} so a
 * connection-loss → reconnection cycle can be exercised in unit tests without a real DB
 * (issue #228). It remains a Spring bean so it can be injected where a controllable probe
 * is wanted, but it is never the primary probe in production.
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
