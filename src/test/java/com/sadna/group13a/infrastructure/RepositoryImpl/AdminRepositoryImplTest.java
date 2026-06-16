package com.sadna.group13a.infrastructure.RepositoryImpl;

import com.sadna.group13a.domain.Aggregates.Admin.Admin;
import com.sadna.group13a.infrastructure.config.PersistenceConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import({AdminRepositoryImpl.class, PersistenceConfig.class})
class AdminRepositoryImplTest {

    @Autowired
    private AdminRepositoryImpl repo;

    @Test
    void givenAdmin_whenSave_thenFindByIdReturnsIt() {
        Admin admin = new Admin("a-1", "u-1");
        repo.save(admin);

        Optional<Admin> found = repo.findById("a-1");
        assertTrue(found.isPresent());
        assertEquals("u-1", found.get().getUserId());
    }

    @Test
    void givenNoAdmin_whenFindById_thenReturnsEmpty() {
        assertTrue(repo.findById("nonexistent").isEmpty());
    }

    @Test
    void givenAdmin_whenFindByUserId_thenReturnsIt() {
        Admin admin = new Admin("a-2", "u-2");
        repo.save(admin);

        Optional<Admin> found = repo.findByUserId("u-2");
        assertTrue(found.isPresent());
        assertEquals("a-2", found.get().getId());
    }

    @Test
    void givenNoAdminWithUserId_whenFindByUserId_thenReturnsEmpty() {
        assertTrue(repo.findByUserId("ghost").isEmpty());
    }

    @Test
    void givenAdmins_whenFindAll_thenReturnsAll() {
        repo.save(new Admin("a-3", "u-3"));
        repo.save(new Admin("a-4", "u-4"));

        List<Admin> all = repo.findAll();
        assertEquals(2, all.size());
    }

    @Test
    void givenAdmin_whenDelete_thenFindByIdReturnsEmpty() {
        Admin admin = new Admin("a-5", "u-5");
        repo.save(admin);

        repo.delete("a-5");

        assertTrue(repo.findById("a-5").isEmpty());
    }

    @Test
    void givenNoAdmins_whenHasAtLeastOneAdmin_thenReturnsFalse() {
        assertFalse(repo.hasAtLeastOneAdmin());
    }

    @Test
    void givenAtLeastOneAdmin_whenHasAtLeastOneAdmin_thenReturnsTrue() {
        repo.save(new Admin("a-6", "u-6"));
        assertTrue(repo.hasAtLeastOneAdmin());
    }
}
