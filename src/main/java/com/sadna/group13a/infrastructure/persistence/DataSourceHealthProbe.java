package com.sadna.group13a.infrastructure.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Real {@link DatabaseHealthProbe} that checks the application's actual JDBC
 * {@link DataSource} (issue #228 / SL-6 recovery).
 *
 * <p>Repositories are JPA-backed (issue #240), so recovery must track the real
 * datasource — not an in-memory abstraction. Each probe borrows a connection from
 * the pool and runs {@link Connection#isValid(int)}; a dropped connection (e.g. a
 * remote PostgreSQL going down) surfaces as {@code false}, which drives
 * {@link DatabaseConnectionManager} into degraded mode. Once the database is
 * reachable again the next probe returns {@code true} and normal operation resumes
 * automatically — no restart required.
 *
 * <p>Marked {@link Primary} so it is the probe wired into the running application;
 * {@link InMemoryDatabaseHealthProbe} remains only as a hand-instantiated test seam.
 */
@Component
@Primary
public class DataSourceHealthProbe implements DatabaseHealthProbe {

    private static final Logger logger = LoggerFactory.getLogger(DataSourceHealthProbe.class);

    /** Seconds allowed for the validity check before the connection is deemed unreachable. */
    private static final int VALIDATION_TIMEOUT_SECONDS = 2;

    private final DataSource dataSource;

    public DataSourceHealthProbe(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean isReachable() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(VALIDATION_TIMEOUT_SECONDS);
        } catch (SQLException e) {
            logger.warn("Datasource health probe failed — treating the database as unreachable: {}", e.getMessage());
            return false;
        }
    }
}
