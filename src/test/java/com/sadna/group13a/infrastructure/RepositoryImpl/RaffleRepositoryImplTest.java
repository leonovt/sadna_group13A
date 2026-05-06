package com.sadna.group13a.infrastructure.RepositoryImpl;

import com.sadna.group13a.domain.Aggregates.Raffle.Raffle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;


class RaffleRepositoryImplTest {

    private RaffleRepositoryImpl repo;

    @BeforeEach
    void setUp() {
        repo = new RaffleRepositoryImpl();
    }

    @Test
    void givenRaffle_whenSave_thenFindByIdReturnsIt() {
        Raffle raffle = new Raffle(UUID.randomUUID().toString(), "event-1", "co-1");
        repo.save(raffle);

        Optional<Raffle> found = repo.findById(raffle.getId());
        assertTrue(found.isPresent());
        assertEquals("event-1", found.get().getEventId());
    }

    @Test
    void givenNoRaffle_whenFindById_thenReturnsEmpty() {
        assertTrue(repo.findById("nonexistent").isEmpty());
    }

    @Test
    void givenRaffle_whenFindByEventId_thenReturnsIt() {
        Raffle raffle = new Raffle(UUID.randomUUID().toString(), "event-42", "co-1");
        repo.save(raffle);

        List<Raffle> found = repo.findByEventId("event-42");
        assertEquals(1, found.size());
        assertEquals("event-42", found.get(0).getEventId());
    }

    @Test
    void givenRaffleForDifferentEvent_whenFindByEventId_thenReturnsEmpty() {
        repo.save(new Raffle(UUID.randomUUID().toString(), "event-A", "co-1"));

        assertTrue(repo.findByEventId("event-B").isEmpty());
    }

    @Test
    void givenTwoRafflesForSameEvent_whenFindByEventId_thenReturnsBoth() {
        repo.save(new Raffle(UUID.randomUUID().toString(), "event-99", "co-1"));
        repo.save(new Raffle(UUID.randomUUID().toString(), "event-99", "co-2"));

        assertEquals(2, repo.findByEventId("event-99").size());
    }
}
