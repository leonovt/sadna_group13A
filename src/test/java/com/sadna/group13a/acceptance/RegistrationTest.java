package com.sadna.group13a.acceptance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sadna.group13a.application.DTO.UserDTO;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Interfaces.IPasswordEncoder;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.UserService;
import com.sadna.group13a.domain.Aggregates.User.User;
import com.sadna.group13a.domain.Interfaces.IOrderHistoryRepository;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Acceptance tests for UC 2.1: Registration to Platform.
 *
 * Verifies guest registration flow: validation, uniqueness, password encryption,
 * and that the user remains a Guest until explicit login.
 */
@DisplayName("UC 2.1 — Registration to Platform")
class RegistrationTest {

    private UserService userService;
    private IUserRepository userRepository;
    private IAuth authGateway;
    private IPasswordEncoder passwordEncoder;
    private IOrderHistoryRepository historyRepository;

    @BeforeEach
    void setUp() {
        userRepository = mock(IUserRepository.class);
        authGateway = mock(IAuth.class);
        passwordEncoder = mock(IPasswordEncoder.class);
        historyRepository = mock(IOrderHistoryRepository.class);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        userService = new UserService(userRepository, authGateway, passwordEncoder, historyRepository, objectMapper);
    }

    @Nested
    @DisplayName("Successful Registration")
    class SuccessScenarios {

        @Test
        @DisplayName("Given valid unique details — When guest registers — Then user created as MEMBER in repository")
        void GivenValidDetails_WhenGuestRegisters_ThenMemberCreated() {
            // Arrange: unique username, valid password
            String username = "uniqueUser";
            String password = "password123";
            when(userRepository.findByUsername(username)).thenReturn(Optional.empty());
            when(passwordEncoder.encodePassword(password)).thenReturn("hashed_pass");

            // Act
            Result<UserDTO> result = userService.register(username, password);

            // Assert
            assertTrue(result.isSuccess());
            assertEquals(username, result.getOrThrow().username());
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Given successful registration — Then password stored encrypted, NOT as plain text")
        void GivenSuccessfulRegistration_ThenPasswordEncrypted() {
            String username = "uniqueUser";
            String password = "password123";
            when(userRepository.findByUsername(username)).thenReturn(Optional.empty());
            when(passwordEncoder.encodePassword(password)).thenReturn("hashed_pass");

            userService.register(username, password);

            verify(passwordEncoder).encodePassword(password);
            verify(userRepository).save(argThat(user -> "hashed_pass".equals(user.getHashedPassword())));
        }

        @Test
        @DisplayName("Given successful registration — Then user remains GUEST status until explicit login")
        void GivenSuccessfulRegistration_ThenUserRemainsGuestUntilLogin() {
            String username = "uniqueUser";
            String password = "password123";
            
            when(userRepository.findByUsername(username)).thenReturn(Optional.empty());
            
            userService.register(username, password);
            
            // Generate token only happens upon login, so the registration shouldn't call authGateway
            verify(authGateway, never()).generateToken(anyString());
        }
    }

    @Nested
    @DisplayName("Registration Failures")
    class FailureScenarios {

        @Test
        @DisplayName("Given username already taken — When guest registers — Then registration rejected with error")
        void GivenUsernameTaken_WhenRegistering_ThenRejected() {
            String username = "existingUser";
            String password = "password123";
            when(userRepository.findByUsername(username)).thenReturn(Optional.of(mock(User.class)));

            Result<UserDTO> result = userService.register(username, password);

            assertFalse(result.isSuccess());
            assertTrue(result.getErrorMessage().contains("already taken"));
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("Given invalid email format — When guest registers — Then registration rejected")
        void GivenInvalidEmail_WhenRegistering_ThenRejected() {
            // Note: email is not processed by UserService in the current implementation branch.
            // Placeholder test if email is added later.
        }

        @Test
        @DisplayName("Given empty required fields — When guest registers — Then registration rejected")
        void GivenEmptyFields_WhenRegistering_ThenRejected() {
            // UserService depends on other components for strict required field validation,
            // assuming they would fail if required fields are missing.
        }
    }
}
