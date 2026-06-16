package com.sadna.group13a.infrastructure.persistence;

import com.sadna.group13a.domain.Aggregates.Event.Event;
import com.sadna.group13a.domain.Interfaces.IEventRepository;
import com.sadna.group13a.domain.shared.PersistenceUnavailableException;
import com.sadna.group13a.infrastructure.RepositoryImpl.EventRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.FakeEventJpaRepository;
import com.sadna.group13a.infrastructure.config.PersistenceConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RepositoryAvailabilityBeanPostProcessor — repository connectivity gate (issue #228)")
class RepositoryAvailabilityBeanPostProcessorTest {

    @Test
    @DisplayName("Repository beans degrade gracefully when disconnected and resume after reconnection")
    void wrapsRepository_andEnforcesConnectivityCycle() {
        InMemoryDatabaseHealthProbe probe = new InMemoryDatabaseHealthProbe();
        DatabaseConnectionManager mgr = new DatabaseConnectionManager(probe);
        RepositoryAvailabilityBeanPostProcessor bpp = new RepositoryAvailabilityBeanPostProcessor(mgr);

        Object wrapped = bpp.postProcessAfterInitialization(
                new EventRepositoryImpl(new FakeEventJpaRepository(), new PersistenceConfig().domainObjectMapper()),
                "eventRepositoryImpl");
        assertTrue(wrapped instanceof IEventRepository, "repository must be wrapped behind its interface");
        IEventRepository repo = (IEventRepository) wrapped;

        // Normal operation
        assertDoesNotThrow(() -> repo.save(new Event("e1", "Title", "Desc", "c1", LocalDateTime.now().plusDays(1), "Music")));
        assertEquals(1, repo.findAll().size());

        // Connection lost → operations rejected with a meaningful error (no crash)
        probe.simulateOutage();
        mgr.monitorConnection();
        assertThrows(PersistenceUnavailableException.class, repo::findAll);

        // Connection restored → resumes automatically, no restart, data intact
        probe.simulateRestore();
        mgr.monitorConnection();
        assertEquals(1, repo.findAll().size());
    }

    @Test
    @DisplayName("Non-repository beans are returned unchanged")
    void nonRepositoryBean_passesThrough() {
        DatabaseConnectionManager mgr = new DatabaseConnectionManager(new InMemoryDatabaseHealthProbe());
        RepositoryAvailabilityBeanPostProcessor bpp = new RepositoryAvailabilityBeanPostProcessor(mgr);

        Object bean = "not-a-repository";
        assertSame(bean, bpp.postProcessAfterInitialization(bean, "someBean"));
    }
}
