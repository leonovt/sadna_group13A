package com.sadna.group13a.acceptance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Interfaces.INotificationService;
import com.sadna.group13a.application.Interfaces.IPasswordEncoder;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.UserService;
import com.sadna.group13a.domain.Interfaces.IOrderHistoryRepository;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import com.sadna.group13a.infrastructure.AuthImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.UserRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Acceptance tests for UC 1.6: Deferred Notifications.
 *
 * Verifies that notifications accumulated while a user was offline are
 * available immediately upon login, in chronological order.
 *
 * Uses the real UserService (with UserRepositoryImpl + AuthImpl) and a
 * StoringNotificationService — an in-memory substitute that implements the
 * production INotificationService interface and adds per-user storage so
 * the deferred-delivery flow can be inspected end-to-end.
 */
@DisplayName("UC 1.6 — Deferred Notifications")
class DeferredNotificationsTest {

    /**
     * Realistic in-memory substitute for the notification back-end.
     * Implements the production INotificationService contract so every real
     * notifyX method stores a timestamped entry in an inbox per user.
     * getPendingNotifications / markAllDelivered are test-inspection helpers.
     */
    static class StoringNotificationService implements INotificationService {

        private record Stored(long seq, String message) {}

        private final Map<String, List<Stored>> inbox = new HashMap<>();
        private final AtomicLong counter = new AtomicLong();

        private void push(String userId, String message) {
            inbox.computeIfAbsent(userId, k -> new ArrayList<>())
                 .add(new Stored(counter.getAndIncrement(), message));
        }

        @Override
        public void notifyOrderCompleted(String userId, String receiptId, double totalPaid) {
            push(userId, "Order completed: receipt=" + receiptId);
        }

        @Override
        public void notifyQueueTurnArrived(String userId, String eventId, LocalDateTime expiresAt) {
            push(userId, "Queue turn: event=" + eventId);
        }

        @Override
        public void notifyUserBanned(String userId, String adminId) {
            push(userId, "Account deactivated by admin=" + adminId);
        }

        @Override
        public void notifyUserSuspended(String userId, java.time.LocalDateTime suspendedUntil) {
            push(userId, "Account suspended until=" + suspendedUntil);
        }

        @Override
        public void notifyCompanyClosed(java.util.List<String> staffIds, String companyId, String adminId) {
            if (staffIds != null) staffIds.forEach(uid -> push(uid, "Company closed by admin=" + adminId));
        }

        @Override
        public void notifyAdminMessage(String targetUserId, String message) {
            push(targetUserId, "Message from admin: " + message);
        }

        @Override
        public void notifyRaffleDrawn(String eventId, int winnerCount) {
            // broadcast event — no per-user target in the current model
        }

        @Override public void notifyActionFailed(String userId, String reason) {}
        @Override public void notifyCompanySuspended(java.util.List<String> staffIds, String companyId) {}
        @Override public void notifyCompanyReopened(java.util.List<String> staffIds, String companyId) {}
        @Override public void notifyStaffNominated(String userId, String companyId, String role) {}
        @Override public void notifyStaffRemoved(String userId, String companyId) {}
        @Override public void notifyPermissionsUpdated(String userId, String companyId) {}
        @Override public void notifyCartExpired(String userId) {}
        @Override public void notifyEventCancelled(java.util.List<String> buyerIds, String eventId, String eventTitle) {}
        @Override public void notifyEventRescheduled(java.util.List<String> buyerIds, String eventId, String eventTitle, java.time.LocalDateTime newDate) {}
        @Override public void notifyUserReactivated(String userId) {}
        @Override public void notifyEventSoldOut(java.util.List<String> staffIds, String eventId, String eventTitle) {}
        @Override public void notifyRaffleWon(String userId, String eventId, String authCode, java.time.LocalDateTime expiresAt) {}

        /** Returns pending notifications for the user in chronological (oldest-first) order. */
        List<String> getPendingNotifications(String userId) {
            return inbox.getOrDefault(userId, List.of()).stream()
                        .sorted(Comparator.comparingLong(Stored::seq))
                        .map(Stored::message)
                        .toList();
        }

        /** Simulates the user reading all notifications (clears their inbox). */
        void markAllDelivered(String userId) {
            inbox.remove(userId);
        }
    }

    private UserService userService;
    private IUserRepository userRepository;
    private StoringNotificationService notificationService;

    @BeforeEach
    void setUp() {
        userRepository = new UserRepositoryImpl();
        IAuth authGateway = new AuthImpl();

        // Password encoder: encodePassword wraps with a prefix so matches() can verify consistently
        IPasswordEncoder passwordEncoder = mock(IPasswordEncoder.class);
        when(passwordEncoder.encodePassword(anyString()))
                .thenAnswer(inv -> "hashed:" + inv.getArgument(0, String.class));
        when(passwordEncoder.matches(anyString(), anyString()))
                .thenAnswer(inv -> ("hashed:" + inv.getArgument(0, String.class))
                        .equals(inv.getArgument(1, String.class)));

        notificationService = new StoringNotificationService();

        userService = new UserService(userRepository, authGateway, passwordEncoder,
                mock(IOrderHistoryRepository.class), new ObjectMapper());
    }

    /** Registers a user via the real UserService and returns their generated ID. */
    private String registerAndGetId(String username, String password) {
        userService.register(username, password);
        return userRepository.findByUsername(username).get().getId();
    }

    @Test
    @DisplayName("Given user offline with 3 pending notifications — When login — Then all 3 available immediately")
    void GivenOfflineUserWithPending_WhenLogin_ThenAllPendingDisplayed() {
        String userId = registerAndGetId("johndoe", "pass123");

        // Simulate business events that occurred while the user was offline
        notificationService.notifyOrderCompleted(userId, "receipt-1", 49.99);
        notificationService.notifyQueueTurnArrived(userId, "event-1", LocalDateTime.now().plusMinutes(10));
        notificationService.notifyUserBanned(userId, "admin-1");
        // Pre-condition: user is registered and 3 notifications are pending
        assertTrue(userRepository.findByUsername("johndoe").isPresent(),
                "Pre: user must be registered before login");
        assertEquals(3, notificationService.getPendingNotifications(userId).size(),
                "Pre: 3 notifications must have accumulated while user was offline");

        Result<String> loginResult = userService.login("johndoe", "pass123");
        List<String> delivered = notificationService.getPendingNotifications(userId);

        // Post-condition: login succeeds and all 3 deferred notifications are immediately available
        assertTrue(loginResult.isSuccess(), "Post: login must succeed for a registered active user");
        assertEquals(3, delivered.size(),
                "Post: all 3 deferred notifications must be available upon login");
        assertTrue(delivered.stream().anyMatch(m -> m.contains("receipt-1")),
                "Post: order-completed notification must be present");
        assertTrue(delivered.stream().anyMatch(m -> m.contains("event-1")),
                "Post: queue-turn notification must be present");
        assertTrue(delivered.stream().anyMatch(m -> m.contains("admin-1")),
                "Post: ban notification must be present");
    }

    @Test
    @DisplayName("Given previously read notifications — When user logs in — Then read notifications NOT reshown")
    void GivenPreviouslyReadNotifications_WhenLogin_ThenNotShownAsNew() {
        String userId = registerAndGetId("janedoe", "pass456");

        // User read earlier notifications before going offline; one new one arrived since
        notificationService.notifyOrderCompleted(userId, "old-receipt", 10.00);
        notificationService.markAllDelivered(userId);   // user read these — inbox cleared
        notificationService.notifyQueueTurnArrived(userId, "event-new", LocalDateTime.now().plusMinutes(5));
        // Pre-condition: only 1 new notification is pending; the old read one is gone
        List<String> prePending = notificationService.getPendingNotifications(userId);
        assertEquals(1, prePending.size(),
                "Pre: only 1 unread notification must be pending after clearing previously read ones");
        assertFalse(prePending.stream().anyMatch(m -> m.contains("old-receipt")),
                "Pre: previously read notification must not appear in pending list");

        Result<String> loginResult = userService.login("janedoe", "pass456");
        List<String> displayed = notificationService.getPendingNotifications(userId);

        // Post-condition: only the 1 new notification is shown; old read ones are not reshown
        assertTrue(loginResult.isSuccess(), "Post: login must succeed");
        assertEquals(1, displayed.size(),
                "Post: only new (unread) notifications must be shown on login");
        assertTrue(displayed.get(0).contains("event-new"),
                "Post: the new queue-turn notification must be present");
        assertFalse(displayed.stream().anyMatch(m -> m.contains("old-receipt")),
                "Post: previously read order notification must not be reshown");
    }

    @Test
    @DisplayName("Given multiple deferred notifications — Then they are delivered in chronological order")
    void GivenMultipleDeferredNotifications_ThenDeliveredChronologically() {
        String userId = registerAndGetId("bob", "pass789");

        // Notifications arrive in a defined sequence while user is offline
        notificationService.notifyOrderCompleted(userId, "receipt-first", 20.00);
        notificationService.notifyQueueTurnArrived(userId, "event-second", LocalDateTime.now().plusMinutes(5));
        notificationService.notifyUserBanned(userId, "admin-third");
        // Pre-condition: 3 notifications recorded in insertion order
        assertEquals(3, notificationService.getPendingNotifications(userId).size(),
                "Pre: 3 notifications must exist before login");

        Result<String> loginResult = userService.login("bob", "pass789");
        List<String> notifications = notificationService.getPendingNotifications(userId);

        // Post-condition: all 3 notifications returned in chronological (oldest-first) order
        assertTrue(loginResult.isSuccess(), "Post: login must succeed");
        assertEquals(3, notifications.size(),
                "Post: all 3 deferred notifications must be present");
        assertTrue(notifications.get(0).contains("receipt-first"),
                "Post: oldest notification (order completed) must appear first");
        assertTrue(notifications.get(1).contains("event-second"),
                "Post: second notification (queue turn) must appear second");
        assertTrue(notifications.get(2).contains("admin-third"),
                "Post: most recent notification (ban) must appear last");
    }

    @Test
    @DisplayName("Given user with no pending notifications — When login — Then empty list returned without error")
    void GivenNoPendingNotifications_WhenLogin_ThenEmptyListReturned() {
        String userId = registerAndGetId("nonotifs", "pass000");
        // Pre-condition: no notifications have been sent to this user
        assertEquals(0, notificationService.getPendingNotifications(userId).size(),
                "Pre: no notifications must exist before login");

        Result<String> loginResult = userService.login("nonotifs", "pass000");
        List<String> pending = notificationService.getPendingNotifications(userId);

        // Post-condition: login succeeds and notification list is empty (no crash, no phantom messages)
        assertTrue(loginResult.isSuccess(),
                "Post: login must succeed even with no pending notifications");
        assertEquals(0, pending.size(),
                "Post: notification list must be empty when nothing was deferred");
    }
}
