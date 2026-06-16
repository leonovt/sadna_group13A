package com.sadna.group13a.infrastructure.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InMemoryDatabaseHealthProbe — outage simulation")
class InMemoryDatabaseHealthProbeTest {

    @Test
    @DisplayName("Reachable by default, togglable via simulateOutage/simulateRestore")
    void togglesReachability() {
        InMemoryDatabaseHealthProbe probe = new InMemoryDatabaseHealthProbe();
        assertTrue(probe.isReachable());

        probe.simulateOutage();
        assertFalse(probe.isReachable());

        probe.simulateRestore();
        assertTrue(probe.isReachable());
    }
}
