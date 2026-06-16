package com.sadna.group13a.infrastructure.persistence;

import com.sadna.group13a.domain.shared.PersistenceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tracks data-store connectivity and provides automatic recovery after a connection
 * loss (issue #228), without requiring a manual restart.
 *
 * <p>This is the project's equivalent of HikariCP connection health-checking: a
 * scheduled monitor periodically probes the data store. When the connection is lost
 * the system enters a degraded mode in which persistence operations are rejected with
 * a {@link PersistenceUnavailableException}; when the connection is restored the monitor
 * flips back to normal operation on its own.
 */
@Component
public class DatabaseConnectionManager {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseConnectionManager.class);

    private final DatabaseHealthProbe probe;
    private final AtomicBoolean connected = new AtomicBoolean(true);

    public DatabaseConnectionManager(DatabaseHealthProbe probe) {
        this.probe = probe;
    }

    /** @return {@code true} while the data store is considered connected. */
    public boolean isConnected() {
        return connected.get();
    }

    /**
     * Guard used at the persistence boundary.
     *
     * @throws PersistenceUnavailableException if the data store is currently disconnected.
     */
    public void verifyConnected() {
        if (!connected.get()) {
            throw new PersistenceUnavailableException(
                    "The data store is currently unavailable; the system is attempting to reconnect. Please retry shortly.");
        }
    }

    /**
     * Periodically checks connectivity and toggles the degraded/normal state.
     * Runs automatically (see {@code @EnableScheduling}); recovery needs no restart.
     */
    @Scheduled(fixedDelayString = "${app.persistence.health-check-interval-ms:5000}")
    public void monitorConnection() {
        boolean reachable;
        try {
            reachable = probe.isReachable();
        } catch (Exception e) {
            reachable = false;
        }

        boolean wasConnected = connected.getAndSet(reachable);
        if (wasConnected && !reachable) {
            logger.error("Data store connection lost — entering degraded mode; persistence operations will be rejected until it is restored.");
        } else if (!wasConnected && reachable) {
            logger.info("Data store connection restored — resuming normal operation (no restart required).");
        }
    }
}
