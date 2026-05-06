package com.sadna.group13a.infrastructure.RepositoryImpl;

import com.sadna.group13a.domain.Aggregates.TicketQueue.TicketQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;


class QueueRepositoryImplTest {

    private QueueRepositoryImpl repo;

    @BeforeEach
    void setUp() {
        repo = new QueueRepositoryImpl();
    }

    @Test
    void givenQueue_whenSave_thenFindByEventIdReturnsIt() {
        TicketQueue queue = new TicketQueue("event-1", 10);
        repo.save(queue);

        Optional<TicketQueue> found = repo.findByEventId("event-1");
        assertTrue(found.isPresent());
        assertEquals(10, found.get().getMaxConcurrentUsers());
    }

    @Test
    void givenNoQueue_whenFindByEventId_thenReturnsEmpty() {
        assertTrue(repo.findByEventId("ghost-event").isEmpty());
    }

    @Test
    void givenTwoQueues_whenFindAll_thenReturnsBoth() {
        repo.save(new TicketQueue("event-A", 5));
        repo.save(new TicketQueue("event-B", 10));

        List<TicketQueue> all = repo.findAll();
        assertEquals(2, all.size());
    }

    @Test
    void givenQueue_whenDelete_thenFindByEventIdReturnsEmpty() {
        repo.save(new TicketQueue("event-del", 5));

        repo.delete("event-del");

        assertTrue(repo.findByEventId("event-del").isEmpty());
    }

    @Test
    void givenQueueSavedTwice_whenFindByEventId_thenLatestValueIsReturned() {
        TicketQueue q1 = new TicketQueue("event-X", 5);
        repo.save(q1);
        q1.adjustMaxConcurrentUsers(20);
        repo.save(q1);

        assertEquals(20, repo.findByEventId("event-X").get().getMaxConcurrentUsers());
    }
}
