package com.sadna.group13a.infrastructure.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies that SL-6 recovery is driven by the <b>real</b> JDBC datasource: a genuine
 * connection drop is detected, and the system auto-recovers once the database is reachable
 * again — exercised end-to-end through {@link DatabaseConnectionManager} (issue #228).
 */
@DisplayName("DataSourceHealthProbe — real datasource connection drop & recovery (SL-6)")
class DataSourceHealthProbeTest {

    @Test
    @DisplayName("Reachable when the datasource hands out a valid connection")
    void reachable_whenConnectionValid() throws SQLException {
        DataSource ds = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        when(ds.getConnection()).thenReturn(conn);
        when(conn.isValid(anyInt())).thenReturn(true);

        assertTrue(new DataSourceHealthProbe(ds).isReachable());
    }

    @Test
    @DisplayName("Unreachable when borrowing a connection throws (real outage)")
    void unreachable_whenGetConnectionThrows() throws SQLException {
        DataSource ds = mock(DataSource.class);
        when(ds.getConnection()).thenThrow(new SQLException("connection refused"));

        assertFalse(new DataSourceHealthProbe(ds).isReachable());
    }

    @Test
    @DisplayName("Unreachable when the borrowed connection reports itself invalid")
    void unreachable_whenConnectionInvalid() throws SQLException {
        DataSource ds = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        when(ds.getConnection()).thenReturn(conn);
        when(conn.isValid(anyInt())).thenReturn(false);

        assertFalse(new DataSourceHealthProbe(ds).isReachable());
    }

    @Test
    @DisplayName("Connection drops then is restored → manager auto-recovers, no restart")
    void datasourceDropThenRestore_autoRecovers() throws SQLException {
        DataSource ds = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        when(conn.isValid(anyInt())).thenReturn(true);
        // First probe: healthy. Then the database goes down (SQLException). Then it comes back.
        when(ds.getConnection())
                .thenReturn(conn)
                .thenThrow(new SQLException("the database is down"))
                .thenReturn(conn);

        DataSourceHealthProbe probe = new DataSourceHealthProbe(ds);
        DatabaseConnectionManager manager = new DatabaseConnectionManager(probe);

        // Healthy probe.
        manager.monitorConnection();
        assertTrue(manager.isConnected());
        assertDoesNotThrow(manager::verifyConnected);

        // Real connection drop is detected → degraded mode rejects persistence calls.
        manager.monitorConnection();
        assertFalse(manager.isConnected());
        assertThrows(com.sadna.group13a.domain.shared.PersistenceUnavailableException.class,
                manager::verifyConnected);

        // Connection restored → next probe resumes normal operation automatically.
        manager.monitorConnection();
        assertTrue(manager.isConnected());
        assertDoesNotThrow(manager::verifyConnected);
    }
}
