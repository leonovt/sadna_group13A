package com.sadna.group13a.infrastructure.RepositoryImpl;

import com.sadna.group13a.infrastructure.PendingNotification;
import com.sadna.group13a.infrastructure.config.PersistenceConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import({PendingNotificationRepositoryImpl.class, PersistenceConfig.class})
@DisplayName("PendingNotificationRepositoryImpl")
class PendingNotificationRepositoryImplTest {

    @Autowired
    private PendingNotificationRepositoryImpl repo;

    @Test
    @DisplayName("findByUserId for unknown user returns empty list")
    void findByUserId_unknownUser_returnsEmpty() {
        assertTrue(repo.findByUserId("nobody").isEmpty());
    }

    @Test
    @DisplayName("save then findByUserId returns the saved notification")
    void saveAndFind_returnsSavedNotification() {
        PendingNotification n = PendingNotification.of("u1", "hello");
        repo.save(n);

        List<PendingNotification> found = repo.findByUserId("u1");
        assertEquals(1, found.size());
        assertEquals("u1", found.get(0).userId());
        assertEquals("hello", found.get(0).message());
    }

    @Test
    @DisplayName("multiple saves for the same user accumulate")
    void multiplesSaves_accumulate() {
        repo.save(PendingNotification.of("u1", "msg1"));
        repo.save(PendingNotification.of("u1", "msg2"));

        assertEquals(2, repo.findByUserId("u1").size());
    }

    @Test
    @DisplayName("deleteByUserId removes all notifications for that user")
    void deleteByUserId_removesAll() {
        repo.save(PendingNotification.of("u1", "msg1"));
        repo.save(PendingNotification.of("u1", "msg2"));
        repo.deleteByUserId("u1");

        assertTrue(repo.findByUserId("u1").isEmpty());
    }

    @Test
    @DisplayName("deleteByUserId does not affect other users")
    void deleteByUserId_doesNotAffectOthers() {
        repo.save(PendingNotification.of("u1", "msg1"));
        repo.save(PendingNotification.of("u2", "msg2"));
        repo.deleteByUserId("u1");

        assertEquals(1, repo.findByUserId("u2").size());
    }
}
