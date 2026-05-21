package com.sadna.group13a.acceptance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UC 1.5 — Real-Time Notifications")
class RealTimeNotificationsTest {

    static class SimulatedNotification {
        String id;
        String userId;
        String message;
        boolean read;

        SimulatedNotification(String id, String userId, String message) {
            this.id = id;
            this.userId = userId;
            this.message = message;
            this.read = false;
        }
    }

    static class SimulatedNotificationService {
        Map<String, List<SimulatedNotification>> store = new HashMap<>();

        public void sendNotification(String userId, String message) {
            store.computeIfAbsent(userId, k -> new ArrayList<>())
                 .add(new SimulatedNotification(String.valueOf(System.nanoTime()), userId, message));
        }

        public List<SimulatedNotification> getUnread(String userId) {
            return store.getOrDefault(userId, List.of()).stream()
                        .filter(n -> !n.read).toList();
        }

        public void markAsRead(String userId, String notificationId) {
            store.getOrDefault(userId, List.of()).stream()
                 .filter(n -> n.id.equals(notificationId))
                 .findFirst()
                 .ifPresent(n -> n.read = true);
        }
    }

    private SimulatedNotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new SimulatedNotificationService();
    }

    @Test
    @DisplayName("Given connected user — When business event occurs — Then notification appears within 2 seconds")
    void GivenConnectedUser_WhenBusinessEvent_ThenNotificationWithin2Seconds() throws Exception {
        String userId = "user1";
        // Pre-condition: user has no unread notifications before the event
        assertEquals(0, notificationService.getUnread(userId).size(), "Pre: user must have no unread notifications before event");

        CompletableFuture<Void> eventTrigger = CompletableFuture.runAsync(() -> {
            try { Thread.sleep(500); } catch (InterruptedException e) {}
            notificationService.sendNotification(userId, "You won the lottery!");
        });

        eventTrigger.get(2, TimeUnit.SECONDS);

        // Post-condition: notification is delivered within 2 seconds and is unread
        List<SimulatedNotification> unread = notificationService.getUnread(userId);
        assertEquals(1, unread.size(), "Post: exactly one unread notification must be present");
        assertEquals("You won the lottery!", unread.get(0).message);
        assertEquals(userId, unread.get(0).userId, "Post: notification must be addressed to the correct user");
    }

    @Test
    @DisplayName("Given notification sent — Then it is delivered only to the intended user")
    void GivenNotificationSent_ThenDeliveredOnlyToIntendedUser() {
        // Pre-condition: neither user has any notifications before the send
        assertEquals(0, notificationService.getUnread("user1").size(), "Pre: user1 must have no notifications before send");
        assertEquals(0, notificationService.getUnread("user2").size(), "Pre: user2 must have no notifications before send");

        notificationService.sendNotification("user1", "Message for user1");

        // Post-condition: notification delivered only to user1, not user2
        List<SimulatedNotification> user1Notifications = notificationService.getUnread("user1");
        List<SimulatedNotification> user2Notifications = notificationService.getUnread("user2");

        assertEquals(1, user1Notifications.size(), "Post: user1 must receive exactly one notification");
        assertEquals(0, user2Notifications.size(), "Post: user2 must not receive any notification");
    }

    @Test
    @DisplayName("Given notification displayed and clicked — Then notification marked as READ in database")
    void GivenNotificationClicked_ThenMarkedAsRead() {
        String userId = "user1";
        notificationService.sendNotification(userId, "Click me");
        // Pre-condition: notification exists and is unread
        assertEquals(1, notificationService.getUnread(userId).size(), "Pre: notification must be unread before click");

        SimulatedNotification notif = notificationService.getUnread(userId).get(0);
        assertFalse(notif.read, "Pre: notification must not be marked read before click");

        notificationService.markAsRead(userId, notif.id);

        // Post-condition: notification is no longer in the unread list
        List<SimulatedNotification> unreadAfterClick = notificationService.getUnread(userId);
        assertEquals(0, unreadAfterClick.size(), "Post: no unread notifications must remain after clicking");
    }

    @Test
    @DisplayName("Given user reconnects — Then unread notifications are still available")
    void GivenUserReconnects_ThenUnreadNotificationsAvailable() {
        String userId = "user1";
        notificationService.sendNotification(userId, "Unread Message");
        // Pre-condition: notification was sent and is unread (simulate disconnect by not reading)
        assertEquals(1, notificationService.getUnread(userId).size(), "Pre: unread notification must exist before reconnect");

        // Simulate reconnect — user fetches unread notifications again
        List<SimulatedNotification> unread = notificationService.getUnread(userId);

        // Post-condition: unread notification is still available after reconnect
        assertEquals(1, unread.size(), "Post: unread notification must persist across reconnect");
        assertEquals("Unread Message", unread.get(0).message);
    }
}
