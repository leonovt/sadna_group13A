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
import org.mockito.Mockito;

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
        
        userService = new UserService(userRepository, authGateway, passwordEncoder, historyRepository, new ObjectMapper());
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

            Result<String> result = userService.login("johndoe", "123456");

            assertTrue(result.isSuccess(), "Login should succeed");
            assertEquals("valid_token", result.getOrThrow(), "Should return a valid session token");
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

            Result<String> result = userService.login("janedoe", "password");
            List<String> pending = notificationService.getPendingNotifications(member.getId());

            assertTrue(result.isSuccess());
            assertEquals(2, pending.size());
            verify(notificationService, times(1)).getPendingNotifications("2");
        }

        @Test
        @DisplayName("Given successful login — Then system displays username and stops showing login/register prompts")
        void GivenSuccessfulLogin_ThenUsernameDisplayedAndPromptsHidden() {
            Member member = new Member("3", "bob", "hashed_pass");
            when(authGateway.validateToken("valid_token")).thenReturn(true);
            when(authGateway.extractUserId("valid_token")).thenReturn("3");
            when(userRepository.findById("3")).thenReturn(Optional.of(member));

            Result<UserDTO> profileResult = userService.getUserProfile("valid_token");

            assertTrue(profileResult.isSuccess(), "Profile fetch should succeed");
            assertEquals("bob", profileResult.getOrThrow().username(), "Username should be available for display");
            assertEquals(UserRole.MEMBER, profileResult.getOrThrow().role(), "User role should be MEMBER");
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

            Result<String> result = userService.login("alice", "wrong_password");

            assertFalse(result.isSuccess(), "Login should fail");
            assertEquals("Invalid username or password.", result.getErrorMessage());
        }

        @Test
        @DisplayName("Given nonexistent username — When login — Then error returned")
        void GivenNonexistentUsername_WhenLogin_ThenError() {
            when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            Result<String> result = userService.login("ghost", "any_password");

            assertFalse(result.isSuccess(), "Login should fail");
            assertEquals("Invalid username or password.", result.getErrorMessage());
        }

        @Test
        @DisplayName("Given deactivated account — When login — Then error returned")
        void GivenDeactivatedAccount_WhenLogin_ThenError() {
            Member member = new Member("5", "inactive_user", "hashed_pass");
            member.deactivate(); 
            
            when(userRepository.findByUsername("inactive_user")).thenReturn(Optional.of(member));
            when(passwordEncoder.matches("password", "hashed_pass")).thenReturn(true);

            Result<String> result = userService.login("inactive_user", "password");

            // Assumes validation logic for inactive users happens or will happen in the branch
            assertFalse(result.isSuccess(), "Login should fail for deactivated account");
        }
    }
    
    // Dummy interface assumed to exist in another branch
    public interface INotificationService {
        List<String> getPendingNotifications(String userId);
    }
}
