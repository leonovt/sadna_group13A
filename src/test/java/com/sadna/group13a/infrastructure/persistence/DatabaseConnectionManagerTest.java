package com.sadna.group13a.infrastructure.persistence;

import com.sadna.group13a.domain.shared.PersistenceUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DatabaseConnectionManager — connection health & auto-recovery (issue #228)")
class DatabaseConnectionManagerTest {

    @Test
    @DisplayName("Starts connected and verifyConnected does not throw")
    void startsConnected() {
        DatabaseConnectionManager mgr = new DatabaseConnectionManager(new InMemoryDatabaseHealthProbe());
        assertTrue(mgr.isConnected());
        assertDoesNotThrow(mgr::verifyConnected);
    }

    @Test
    @DisplayName("Connection loss → degraded mode; verifyConnected throws a meaningful error")
    void connectionLoss_entersDegradedMode() {
        InMemoryDatabaseHealthProbe probe = new InMemoryDatabaseHealthProbe();
        DatabaseConnectionManager mgr = new DatabaseConnectionManager(probe);

        probe.simulateOutage();
        mgr.monitorConnection();

        assertFalse(mgr.isConnected());
        PersistenceUnavailableException ex = assertThrows(PersistenceUnavailableException.class, mgr::verifyConnected);
        assertNotNull(ex.getMessage());
    }

    @Test
    @DisplayName("Connection restored → resumes automatically without restart")
    void connectionRestored_autoRecovers() {
        InMemoryDatabaseHealthProbe probe = new InMemoryDatabaseHealthProbe();
        DatabaseConnectionManager mgr = new DatabaseConnectionManager(probe);

        probe.simulateOutage();
        mgr.monitorConnection();
        assertFalse(mgr.isConnected());

        probe.simulateRestore();
        mgr.monitorConnection();

        assertTrue(mgr.isConnected());
        assertDoesNotThrow(mgr::verifyConnected);
    }

    @Test
    @DisplayName("A failing probe is treated as a lost connection")
    void probeThrowing_treatedAsDisconnected() {
        DatabaseHealthProbe throwingProbe = () -> { throw new RuntimeException("probe blew up"); };
        DatabaseConnectionManager mgr = new DatabaseConnectionManager(throwingProbe);

        mgr.monitorConnection();

        assertFalse(mgr.isConnected());
    }
}
