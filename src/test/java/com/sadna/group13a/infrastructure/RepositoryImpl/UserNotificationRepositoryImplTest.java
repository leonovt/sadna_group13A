package com.sadna.group13a.infrastructure.RepositoryImpl;

import com.sadna.group13a.infrastructure.UserNotification;
import com.sadna.group13a.infrastructure.config.PersistenceConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import({UserNotificationRepositoryImpl.class, PersistenceConfig.class})
@DisplayName("UserNotificationRepositoryImpl")
class UserNotificationRepositoryImplTest {

    @Autowired
    private UserNotificationRepositoryImpl repo;

    @Test
    @DisplayName("findByUserId for unknown user returns empty list")
    void findByUser_unknownUser_returnsEmpty() {
        assertTrue(repo.findByUserIdOrderByCreatedAtDesc("nobody").isEmpty());
    }

    @Test
    @DisplayName("save then find returns the saved notification")
    void saveAndFind_returnsSaved() {
        repo.save(UserNotification.general("u1", "hello"));

        List<UserNotification> found = repo.findByUserIdOrderByCreatedAtDesc("u1");
        assertEquals(1, found.size());
        assertEquals("hello", found.get(0).message());
    }

    @Test
    @DisplayName("deleteById removes only that notification")
    void deleteById_removesOne() {
        UserNotification n = UserNotification.general("u1", "msg");
        repo.save(n);
        repo.deleteById(n.id());

        assertTrue(repo.findByUserIdOrderByCreatedAtDesc("u1").isEmpty());
    }

    @Test
    @DisplayName("Issue #368 — deleteNominations removes the user's nomination for that company")
    void deleteNominations_removesMatchingNomination() {
        repo.save(UserNotification.nomination("u1", "You were invited to Acme", "c1"));

        repo.deleteNominations("u1", "c1");

        assertTrue(repo.findByUserIdOrderByCreatedAtDesc("u1").isEmpty(),
                "Accepted/rejected nomination must be deleted so it does not reappear on refresh");
    }

    @Test
    @DisplayName("Issue #368 — deleteNominations leaves general notifications and other companies untouched")
    void deleteNominations_isScopedToUserCompanyAndType() {
        repo.save(UserNotification.nomination("u1", "Invite to c1", "c1"));   // target
        repo.save(UserNotification.nomination("u1", "Invite to c2", "c2"));   // different company
        repo.save(UserNotification.general("u1", "Welcome"));                 // different type
        repo.save(UserNotification.nomination("u2", "Invite to c1", "c1"));   // different user

        repo.deleteNominations("u1", "c1");

        List<UserNotification> u1 = repo.findByUserIdOrderByCreatedAtDesc("u1");
        assertEquals(2, u1.size(), "Only the c1 nomination for u1 must be removed");
        assertTrue(u1.stream().noneMatch(
                n -> UserNotification.TYPE_STAFF_NOMINATION.equals(n.type()) && "c1".equals(n.metadata())),
                "The c1 nomination for u1 must be gone");
        assertTrue(u1.stream().anyMatch(n -> "c2".equals(n.metadata())), "Other company's nomination must remain");
        assertTrue(u1.stream().anyMatch(n -> UserNotification.TYPE_GENERAL.equals(n.type())), "General notification must remain");
        assertEquals(1, repo.findByUserIdOrderByCreatedAtDesc("u2").size(), "Another user's nomination must remain");
    }

    @Test
    @DisplayName("deleteNominations on a user with no matching nomination is a no-op")
    void deleteNominations_noMatch_noOp() {
        repo.save(UserNotification.general("u1", "Welcome"));

        assertDoesNotThrow(() -> repo.deleteNominations("u1", "c1"));
        assertEquals(1, repo.findByUserIdOrderByCreatedAtDesc("u1").size());
    }
}
