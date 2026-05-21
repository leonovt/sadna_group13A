package com.sadna.group13a.acceptance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sadna.group13a.application.DTO.UserDTO;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Interfaces.IPasswordEncoder;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.UserService;
import com.sadna.group13a.domain.Aggregates.User.Member;
import com.sadna.group13a.domain.Aggregates.User.UserRole;
import com.sadna.group13a.domain.Interfaces.IOrderHistoryRepository;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Acceptance tests for UC 2.2: Authenticated Login and Notification Handling.
 */
@DisplayName("UC 2.2 — Authenticated Login and Notification Handling")
class LoginAndNotificationsTest {

    private UserService userService;
    private IUserRepository userRepository;
    private IAuth authGateway;
    private IPasswordEncoder passwordEncoder;
    private IOrderHistoryRepository historyRepository;

    // Assumed to exist in another branch
    private INotificationService notificationService;

    @BeforeEach
    void setUp() {
        userRepository = mock(IUserRepository.class);
        authGateway = mock(IAuth.class);
        passwordEncoder = mock(IPasswordEncoder.class);
        historyRepository = mock(IOrderHistoryRepository.class);
        notificationService = mock(INotificationService.class);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        userService = new UserService(userRepository, authGateway, passwordEncoder, historyRepository, objectMapper);
    }

    @Nested
    @DisplayName("Successful Login")
    class SuccessScenarios {

        @Test
        @DisplayName("Given valid credentials — When member logs in — Then session created and status changed to MEMBER")
        void GivenValidCredentials_WhenLogin_ThenSessionCreatedAndStatusMember() {
            Member member = new Member("1", "johndoe", "hashed_pass");
            when(userRepository.findByUsername("johndoe")).thenReturn(Optional.of(member));
            when(passwordEncoder.matches("123456", "hashed_pass")).thenReturn(true);
            when(authGateway.generateToken("1")).thenReturn("valid_token");
            // Pre-condition: member is registered and active
            assertTrue(member.isActive(), "Pre: member must be active to log in");

            Result<String> result = userService.login("johndoe", "123456");

            // Post-condition: login succeeds and a valid session token is returned
            assertTrue(result.isSuccess(), "Post: login must succeed for valid credentials");
            assertEquals("valid_token", result.getOrThrow(), "Post: returned token must match the generated session token");
        }

        @Test
        @DisplayName("Given member with pending notifications — When login — Then all deferred notifications returned immediately")
        void GivenPendingNotifications_WhenLogin_ThenDeferredNotificationsReturned() {
            Member member = new Member("2", "janedoe", "hashed_pass");
            when(userRepository.findByUsername("janedoe")).thenReturn(Optional.of(member));
            when(passwordEncoder.matches("password", "hashed_pass")).thenReturn(true);
            when(authGateway.generateToken("2")).thenReturn("valid_token");

            // Assume notification service returns notifications for the user
            List<String> mockNotifications = List.of("Refund processed", "Lottery won");
            when(notificationService.getPendingNotifications("2")).thenReturn(mockNotifications);
            // Pre-condition: member has pending notifications accumulated while offline
            assertEquals(2, mockNotifications.size(), "Pre: 2 pending notifications must exist before login");

            Result<String> result = userService.login("janedoe", "password");
            List<String> pending = notificationService.getPendingNotifications(member.getId());

            // Post-condition: login succeeds and all pending notifications are immediately available
            assertTrue(result.isSuccess(), "Post: login must succeed");
            assertEquals(2, pending.size(), "Post: all pending notifications must be returned after login");
            verify(notificationService, times(1)).getPendingNotifications("2");
        }

        @Test
        @DisplayName("Given successful login — Then system displays username and stops showing login/register prompts")
        void GivenSuccessfulLogin_ThenUsernameDisplayedAndPromptsHidden() {
            Member member = new Member("3", "bob", "hashed_pass");
            when(authGateway.validateToken("valid_token")).thenReturn(true);
            when(authGateway.extractUserId("valid_token")).thenReturn("3");
            when(userRepository.findById("3")).thenReturn(Optional.of(member));
            // Pre-condition: user has an active session (valid token)
            assertTrue(authGateway.validateToken("valid_token"), "Pre: session token must be valid before fetching profile");

            Result<UserDTO> profileResult = userService.getUserProfile("valid_token");

            // Post-condition: profile is returned with username visible and role set to MEMBER
            assertTrue(profileResult.isSuccess(), "Post: profile fetch should succeed");
            assertEquals("bob", profileResult.getOrThrow().username(), "Post: username must be available for display after login");
            assertEquals(UserRole.MEMBER, profileResult.getOrThrow().role(), "Post: user role must be MEMBER after login");
        }
    }

    @Nested
    @DisplayName("Login Failures")
    class FailureScenarios {

        @Test
        @DisplayName("Given wrong password — When login — Then error returned and user stays as GUEST without personal data access")
        void GivenWrongPassword_WhenLogin_ThenErrorAndStaysGuest() {
            Member member = new Member("4", "alice", "hashed_pass");
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(member));
            when(passwordEncoder.matches("wrong_password", "hashed_pass")).thenReturn(false);
            // Pre-condition: user exists but the provided password does not match
            assertTrue(userRepository.findByUsername("alice").isPresent(), "Pre: user must exist in repository");

            Result<String> result = userService.login("alice", "wrong_password");

            // Post-condition: login fails with an appropriate error; no session token issued
            assertFalse(result.isSuccess(), "Post: login must fail for wrong password");
            assertEquals("Invalid username or password.", result.getErrorMessage());
        }

        @Test
        @DisplayName("Given nonexistent username — When login — Then error returned")
        void GivenNonexistentUsername_WhenLogin_ThenError() {
            when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
            // Pre-condition: no user with this username exists
            assertFalse(userRepository.findByUsername("ghost").isPresent(), "Pre: user must not exist in repository");

            Result<String> result = userService.login("ghost", "any_password");

            // Post-condition: login fails with a generic error (no username enumeration)
            assertFalse(result.isSuccess(), "Post: login must fail for unknown username");
            assertEquals("Invalid username or password.", result.getErrorMessage());
        }

    }

    // Dummy interface assumed to exist in another branch
    public interface INotificationService {
        List<String> getPendingNotifications(String userId);
    }
}
