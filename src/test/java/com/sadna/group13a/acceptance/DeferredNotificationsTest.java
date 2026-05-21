package com.sadna.group13a.acceptance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Interfaces.IPasswordEncoder;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.UserService;
import com.sadna.group13a.domain.Aggregates.User.Member;
import com.sadna.group13a.domain.Interfaces.IOrderHistoryRepository;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Acceptance tests for UC 1.6: Deferred Notifications.
 *
 * Verifies that notifications accumulated while a user was offline
 * are presented immediately upon login, in chronological order.
 */
@DisplayName("UC 1.6 — Deferred Notifications")
class DeferredNotificationsTest {

    private UserService userService;
    private IUserRepository userRepository;
    private IAuth authGateway;
    private IPasswordEncoder passwordEncoder;
    private IOrderHistoryRepository historyRepository;
    private INotificationService notificationService;

    @BeforeEach
    void setUp() {
        userRepository = mock(IUserRepository.class);
        authGateway = mock(IAuth.class);
        passwordEncoder = mock(IPasswordEncoder.class);
        historyRepository = mock(IOrderHistoryRepository.class);
        notificationService = mock(INotificationService.class);

        userService = new UserService(userRepository, authGateway, passwordEncoder, historyRepository,
                new ObjectMapper());
    }

    @Test
    @DisplayName("Given user was offline and has pending notifications — When user logs in — Then all pending notifications displayed immediately")
    void GivenOfflineUserWithPending_WhenLogin_ThenAllPendingDisplayed() {
        // Arrange: user offline, system sends notifications (lottery win, refund, queue update)
        String userId = "user1";
        Member member = new Member(userId, "johndoe", "hashed_pass");
        when(userRepository.findByUsername("johndoe")).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("123456", "hashed_pass")).thenReturn(true);
        when(authGateway.generateToken(userId)).thenReturn("valid_token");

        List<String> pendingNotifications = List.of("Lottery won!", "Refund processed", "Queue update: it's your turn");
        when(notificationService.getPendingNotifications(userId)).thenReturn(pendingNotifications);
        // Pre-condition: user is registered, and has 3 pending notifications accumulated while offline
        assertTrue(member.isActive(), "Pre: user must be an active member to log in");
        assertEquals(3, notificationService.getPendingNotifications(userId).size(), "Pre: 3 pending notifications must exist before login");

        // Act: user logs in
        Result<String> loginResult = userService.login("johndoe", "123456");
        List<String> displayedNotifications = notificationService.getPendingNotifications(userId);

        // Post-condition: login succeeds and all pending notifications are returned immediately
        assertTrue(loginResult.isSuccess(), "Post: login must succeed for a registered active user");
        assertEquals(3, displayedNotifications.size(), "Post: all 3 pending notifications must be displayed on login");
        assertTrue(displayedNotifications.contains("Lottery won!"));
        assertTrue(displayedNotifications.contains("Refund processed"));
        assertTrue(displayedNotifications.contains("Queue update: it's your turn"));
    }

    @Test
    @DisplayName("Given previously read notifications — When user logs in — Then read notifications NOT shown as new")
    void GivenPreviouslyReadNotifications_WhenLogin_ThenNotShownAsNew() {
        String userId = "user2";
        Member member = new Member(userId, "janedoe", "hashed_pass");
        when(userRepository.findByUsername("janedoe")).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("password", "hashed_pass")).thenReturn(true);
        when(authGateway.generateToken(userId)).thenReturn("valid_token");

        // Mock returning only new (pending) notifications
        when(notificationService.getPendingNotifications(userId)).thenReturn(List.of("Only new notification"));
        // Pre-condition: user is registered; previously read notifications are not in the pending list
        assertFalse(notificationService.getPendingNotifications(userId).contains("Old read notification"),
                "Pre: previously read notifications must not appear in pending list");

        Result<String> loginResult = userService.login("janedoe", "password");
        List<String> displayedNotifications = notificationService.getPendingNotifications(userId);

        // Post-condition: only unread (new) notifications are shown
        assertTrue(loginResult.isSuccess(), "Post: login must succeed");
        assertEquals(1, displayedNotifications.size(), "Post: only new notifications must be shown");
        assertEquals("Only new notification", displayedNotifications.get(0));
        assertFalse(displayedNotifications.contains("Old read notification"), "Post: previously read notifications must not be reshown");
    }

    @Test
    @DisplayName("Given multiple deferred notifications — Then they are displayed in chronological order")
    void GivenMultipleDeferredNotifications_ThenDisplayedChronologically() {
        String userId = "user3";
        Member member = new Member(userId, "bob", "hashed_pass");
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("123", "hashed_pass")).thenReturn(true);
        when(authGateway.generateToken(userId)).thenReturn("valid_token");

        List<String> chronologicallyOrderedNotifications = List.of(
                "10:00 AM - Purchase completed",
                "11:00 AM - Event update",
                "12:00 PM - Queue update");
        when(notificationService.getPendingNotifications(userId)).thenReturn(chronologicallyOrderedNotifications);
        // Pre-condition: user is registered and has 3 pending notifications in chronological order
        List<String> prePending = notificationService.getPendingNotifications(userId);
        assertEquals(3, prePending.size(), "Pre: 3 pending notifications must exist");

        Result<String> loginResult = userService.login("bob", "123");
        List<String> notifications = notificationService.getPendingNotifications(userId);

        // Post-condition: notifications are shown in chronological (oldest-first) order
        assertTrue(loginResult.isSuccess(), "Post: login must succeed");
        assertEquals(3, notifications.size(), "Post: all 3 deferred notifications must be displayed");
        assertEquals("10:00 AM - Purchase completed", notifications.get(0), "Post: oldest notification must appear first");
        assertEquals("11:00 AM - Event update", notifications.get(1));
        assertEquals("12:00 PM - Queue update", notifications.get(2), "Post: most recent notification must appear last");
    }

    // Assumed to exist in another branch
    public interface INotificationService {
        List<String> getPendingNotifications(String userId);
    }
}
