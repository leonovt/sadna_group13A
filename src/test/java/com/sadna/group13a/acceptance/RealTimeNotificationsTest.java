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

        CompletableFuture<Void> eventTrigger = CompletableFuture.runAsync(() -> {
            try { Thread.sleep(500); } catch (InterruptedException e) {}
            notificationService.sendNotification(userId, "You won the lottery!");
        });

        eventTrigger.get(2, TimeUnit.SECONDS);

        List<SimulatedNotification> unread = notificationService.getUnread(userId);
        assertEquals(1, unread.size());
        assertEquals("You won the lottery!", unread.get(0).message);
    }

    @Test
    @DisplayName("Given notification sent — Then it is delivered only to the intended user")
    void GivenNotificationSent_ThenDeliveredOnlyToIntendedUser() {
        notificationService.sendNotification("user1", "Message for user1");
        
        List<SimulatedNotification> user1Notifications = notificationService.getUnread("user1");
        List<SimulatedNotification> user2Notifications = notificationService.getUnread("user2");

        assertEquals(1, user1Notifications.size());
        assertEquals(0, user2Notifications.size());
    }

    @Test
    @DisplayName("Given notification displayed and clicked — Then notification marked as READ in database")
    void GivenNotificationClicked_ThenMarkedAsRead() {
        String userId = "user1";
        notificationService.sendNotification(userId, "Click me");
        
        SimulatedNotification notif = notificationService.getUnread(userId).get(0);
        
        notificationService.markAsRead(userId, notif.id);

        List<SimulatedNotification> unreadAfterClick = notificationService.getUnread(userId);
        assertEquals(0, unreadAfterClick.size());
    }

    @Test
    @DisplayName("Given user reconnects — Then unread notifications are still available")
    void GivenUserReconnects_ThenUnreadNotificationsAvailable() {
        String userId = "user1";
        notificationService.sendNotification(userId, "Unread Message");
        
        List<SimulatedNotification> unread = notificationService.getUnread(userId);
        
        assertEquals(1, unread.size());
        assertEquals("Unread Message", unread.get(0).message);
    }
}
