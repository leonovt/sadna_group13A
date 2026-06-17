package com.sadna.group13a.application.Services;

import com.sadna.group13a.domain.Interfaces.IPendingNotificationRepository;
import com.sadna.group13a.infrastructure.PendingNotification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for the bug where navigating to any page threw
 * {@code TransactionRequiredException}: HomeView.onAttach → HomePresenter →
 * NotificationBroadcaster.register → repository.deleteByUserId ran a JPA delete with no
 * active transaction.
 *
 * <p>The full context is booted under the {@code test} profile and the test method is
 * intentionally <b>not</b> {@code @Transactional}, so it reproduces the exact runtime
 * conditions of the Vaadin UI thread:
 * <ul>
 *   <li>calling the repository's derived {@code deleteByUserId} directly (as the broadcaster
 *       used to) fails clearly with a transaction error;</li>
 *   <li>the new {@link PendingNotificationService#drainPending(String)} succeeds because it
 *       owns its transaction, reading and clearing the inbox atomically.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Deferred-notification drain runs in a transaction (page-load regression)")
class PendingNotificationServiceIntegrationTest {

    @Autowired private PendingNotificationService pendingNotificationService;
    @Autowired private IPendingNotificationRepository repository;

    @Test
    @DisplayName("drainPending reads and clears the inbox within its own transaction")
    void drainPending_succeedsAndClears() {
        String userId = "drain-user-1";
        // save() on the Spring Data CRUD method is itself transactional, so seeding works.
        repository.save(PendingNotification.of(userId, "Refund processed while you were away"));
        repository.save(PendingNotification.of(userId, "Your event was rescheduled"));

        List<String> drained = pendingNotificationService.drainPending(userId);

        assertEquals(2, drained.size(), "both stored messages should be returned");
        assertTrue(drained.contains("Refund processed while you were away"));
        assertTrue(drained.contains("Your event was rescheduled"));

        // Inbox is now cleared — a second drain returns nothing and does not throw.
        assertTrue(pendingNotificationService.drainPending(userId).isEmpty());
        assertTrue(repository.findByUserId(userId).isEmpty());
    }

    @Test
    @DisplayName("drainPending on an empty/unknown inbox returns empty, never throws")
    void drainPending_emptyInbox() {
        assertTrue(pendingNotificationService.drainPending("nobody-here").isEmpty());
        assertTrue(pendingNotificationService.drainPending(null).isEmpty());
    }

    @Test
    @DisplayName("Reproduces the bug: a direct deleteByUserId with no active transaction fails clearly")
    void rawDeleteWithoutTransaction_failsClearly() {
        String userId = "drain-user-2";
        repository.save(PendingNotification.of(userId, "msg"));

        // This is exactly what the broadcaster used to do on the Vaadin UI thread: a derived
        // JPA delete with no surrounding transaction. It must fail loudly rather than silently.
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> repository.deleteByUserId(userId));
        assertTrue(ex.getMessage() != null && ex.getMessage().toLowerCase().contains("transaction"),
                "failure must clearly indicate the missing transaction; was: " + ex.getMessage());

        // And the proper, transactional path still works afterwards.
        assertEquals(List.of("msg"), pendingNotificationService.drainPending(userId));
    }
}
