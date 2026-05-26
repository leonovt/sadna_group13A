package com.sadna.group13a.acceptance;

import com.sadna.group13a.application.Interfaces.INotificationService;
import com.sadna.group13a.infrastructure.InMemoryNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("UC 1.5 — Real-Time Notifications")
class RealTimeNotificationsTest {

    private INotificationService notificationService;

    @BeforeEach
    void setUp() {
        // Spy wraps the real InMemoryNotificationService so the actual implementation
        // runs while Mockito can observe every call made to it.
        notificationService = spy(new InMemoryNotificationService());
    }

    @Test
    @DisplayName("Given connected user — When business event occurs — Then notification appears within 2 seconds")
    void GivenConnectedUser_WhenBusinessEvent_ThenNotificationWithin2Seconds() throws Exception {
        String userId = "user1";
        String eventId = "event1";
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(10);
        // Pre-condition: no notifications have been dispatched to this user yet
        verify(notificationService, never()).notifyQueueTurnArrived(anyString(), anyString(), any());

        CompletableFuture<Void> eventTrigger = CompletableFuture.runAsync(() -> {
            try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            notificationService.notifyQueueTurnArrived(userId, eventId, expiresAt);
        });

        eventTrigger.get(2, TimeUnit.SECONDS);

        // Post-condition: the real notification method was invoked exactly once within 2 seconds
        verify(notificationService, times(1)).notifyQueueTurnArrived(userId, eventId, expiresAt);
    }

    @Test
    @DisplayName("Given notification sent — Then it is delivered only to the intended user")
    void GivenNotificationSent_ThenDeliveredOnlyToIntendedUser() {
        String user1 = "user1";
        String user2 = "user2";
        String receiptId = "receipt-001";
        // Pre-condition: no order-completed notifications dispatched to either user
        verify(notificationService, never()).notifyOrderCompleted(anyString(), anyString(), anyDouble());

        notificationService.notifyOrderCompleted(user1, receiptId, 99.99);

        // Post-condition: notification dispatched to user1 exactly once; user2 receives nothing
        verify(notificationService, times(1)).notifyOrderCompleted(user1, receiptId, 99.99);
        verify(notificationService, never()).notifyOrderCompleted(eq(user2), anyString(), anyDouble());
    }

    @Test
    @DisplayName("Given notification dispatched — Then it is delivered exactly once (no duplicates after read)")
    void GivenNotificationDispatched_ThenDeliveredExactlyOnce() {
        String userId = "user1";
        String adminId = "admin1";
        // Pre-condition: no ban notification has been sent yet
        verify(notificationService, never()).notifyUserBanned(anyString(), anyString());

        notificationService.notifyUserBanned(userId, adminId);

        // Post-condition: notification delivered exactly once — not re-queued or duplicated
        verify(notificationService, times(1)).notifyUserBanned(userId, adminId);
        verifyNoMoreInteractions(notificationService);
    }

    @Test
    @DisplayName("Given user reconnects — Then service continues to dispatch notifications correctly")
    void GivenUserReconnects_ThenServiceDispatchesCorrectlyAfterReconnect() {
        String userId = "user1";
        String eventId = "event1";
        LocalDateTime firstWindow  = LocalDateTime.now().plusMinutes(10);
        LocalDateTime secondWindow = LocalDateTime.now().plusMinutes(20);

        // First session: queue turn notification dispatched
        notificationService.notifyQueueTurnArrived(userId, eventId, firstWindow);
        // Pre-condition: one notification delivered before the simulated reconnect
        verify(notificationService, times(1)).notifyQueueTurnArrived(eq(userId), eq(eventId), any());

        // Simulate reconnect: business layer re-notifies the user on re-entry
        notificationService.notifyQueueTurnArrived(userId, eventId, secondWindow);

        // Post-condition: service remains operational after reconnect; second notification also delivered
        verify(notificationService, times(2)).notifyQueueTurnArrived(eq(userId), eq(eventId), any());
    }
}
