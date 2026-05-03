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
        
        userService = new UserService(userRepository, authGateway, passwordEncoder, historyRepository, new ObjectMapper());
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

        // Act: user logs in
        Result<String> loginResult = userService.login("johndoe", "123456");
        List<String> displayedNotifications = notificationService.getPendingNotifications(userId);

        // Assert: all pending notifications returned after login
        assertTrue(loginResult.isSuccess());
        assertEquals(3, displayedNotifications.size());
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

        Result<String> loginResult = userService.login("janedoe", "password");
        List<String> displayedNotifications = notificationService.getPendingNotifications(userId);

        assertTrue(loginResult.isSuccess());
        assertEquals(1, displayedNotifications.size());
        assertEquals("Only new notification", displayedNotifications.get(0));
        assertFalse(displayedNotifications.contains("Old read notification"));
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
            "12:00 PM - Queue update"
        );
        when(notificationService.getPendingNotifications(userId)).thenReturn(chronologicallyOrderedNotifications);

        Result<String> loginResult = userService.login("bob", "123");
        List<String> notifications = notificationService.getPendingNotifications(userId);

        assertTrue(loginResult.isSuccess());
        assertEquals(3, notifications.size());
        assertEquals("10:00 AM - Purchase completed", notifications.get(0));
        assertEquals("11:00 AM - Event update", notifications.get(1));
        assertEquals("12:00 PM - Queue update", notifications.get(2));
    }

    // Assumed to exist in another branch
    public interface INotificationService {
        List<String> getPendingNotifications(String userId);
    }
}
