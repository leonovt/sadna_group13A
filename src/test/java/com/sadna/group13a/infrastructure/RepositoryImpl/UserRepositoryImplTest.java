package com.sadna.group13a.infrastructure.RepositoryImpl;

import com.sadna.group13a.domain.Aggregates.User.Admin;
import com.sadna.group13a.domain.Aggregates.User.Member;
import com.sadna.group13a.domain.Aggregates.User.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure JUnit 5 — no Mockito, no Spring.
 * Tests the actual ConcurrentHashMap-backed UserRepositoryImpl.
 */
class UserRepositoryImplTest {

    private UserRepositoryImpl repo;

    @BeforeEach
    void setUp() {
        repo = new UserRepositoryImpl();
    }

    @Test
    void givenUser_whenSave_thenFindByIdReturnsIt() {
        Member user = new Member("u-1", "alice", "hash");
        repo.save(user);

        Optional<User> found = repo.findById("u-1");
        assertTrue(found.isPresent());
        assertEquals("alice", found.get().getUsername());
    }

    @Test
    void givenNoUser_whenFindById_thenReturnsEmpty() {
        assertTrue(repo.findById("nonexistent").isEmpty());
    }

    @Test
    void givenUser_whenFindByUsername_thenReturnsIt() {
        Member user = new Member("u-2", "bob", "hash");
        repo.save(user);

        Optional<User> found = repo.findByUsername("bob");
        assertTrue(found.isPresent());
        assertEquals("u-2", found.get().getId());
    }

    @Test
    void givenNoUser_whenFindByUsername_thenReturnsEmpty() {
        assertTrue(repo.findByUsername("ghost").isEmpty());
    }

    @Test
    void givenUser_whenDelete_thenFindByIdReturnsEmpty() {
        Member user = new Member("u-3", "carol", "hash");
        repo.save(user);

        repo.delete("u-3");

        assertTrue(repo.findById("u-3").isEmpty());
    }

    @Test
    void givenTwoUsers_whenFindAll_thenReturnsBoth() {
        repo.save(new Member("u-4", "dave", "hash"));
        repo.save(new Admin("u-5", "admin", "hash"));

        List<User> all = repo.findAll();
        assertEquals(2, all.size());
    }

    @Test
    void givenUser_whenExistsByUsername_thenReturnsTrue() {
        repo.save(new Member("u-6", "eve", "hash"));

        assertTrue(repo.existsByUsername("eve"));
        assertFalse(repo.existsByUsername("nobody"));
    }

    @Test
    void givenSavedUser_whenSavedAgainWithSameId_thenOverwrites() {
        Member v1 = new Member("u-7", "frank", "hash1");
        repo.save(v1);
        v1.setUsername("frank-updated");
        repo.save(v1);

        assertEquals("frank-updated", repo.findById("u-7").get().getUsername());
    }
}
