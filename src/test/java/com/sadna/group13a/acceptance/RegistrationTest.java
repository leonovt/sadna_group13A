package com.sadna.group13a.acceptance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sadna.group13a.application.DTO.UserDTO;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Interfaces.IPasswordEncoder;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.UserService;
import com.sadna.group13a.domain.Aggregates.User.UserRole;
import com.sadna.group13a.domain.Interfaces.IOrderHistoryRepository;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Acceptance tests for UC 2.1: User Registration.
 *
 * Covers the happy path (valid credentials) and five rejection scenarios:
 * duplicate username, null/blank username, null/blank password.
 */
@DisplayName("UC 2.1 — User Registration")
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
        objectMapper.configure(
                com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        userService = new UserService(userRepository, authGateway, passwordEncoder, historyRepository, objectMapper);
    }

    // ── Positive ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Given new unique username and valid password — When registering — Then user created and DTO returned")
    void GivenValidCredentials_WhenRegistering_ThenUserCreatedAndDTOReturned() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(passwordEncoder.encodePassword("secret")).thenReturn("hashed_secret");
        // Pre-condition: username does not already exist in the system
        assertTrue(userRepository.findByUsername("alice").isEmpty(),
                "Pre: username must not already be registered");

        Result<UserDTO> result = userService.register("alice", "secret");

        // Post-condition: registration succeeds; user is saved; DTO carries the username and MEMBER role
        assertTrue(result.isSuccess(), "Post: registration must succeed for a unique username");
        assertEquals("alice", result.getOrThrow().username(),
                "Post: returned DTO must carry the registered username");
        assertEquals(UserRole.MEMBER, result.getOrThrow().role(),
                "Post: newly registered user must have MEMBER role");
        verify(userRepository).save(argThat(u -> "alice".equals(u.getUsername())));
    }

    // ── Negative ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Given username already taken — When registering — Then registration rejected")
    void GivenDuplicateUsername_WhenRegistering_ThenRejected() {
        com.sadna.group13a.domain.Aggregates.User.Member existing =
                new com.sadna.group13a.domain.Aggregates.User.Member("id1", "bob", "hash");
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(existing));
        // Pre-condition: "bob" is already registered
        assertTrue(userRepository.findByUsername("bob").isPresent(),
                "Pre: username must already exist to trigger duplicate rejection");

        Result<UserDTO> result = userService.register("bob", "password");

        // Post-condition: registration fails with a clear duplicate-username message
        assertFalse(result.isSuccess(), "Post: duplicate username must be rejected");
        assertTrue(result.getErrorMessage().contains("already taken"),
                "Post: error message must indicate the username is taken");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Given null username — When registering — Then registration rejected")
    void GivenNullUsername_WhenRegistering_ThenRejected() {
        // Pre-condition: no username is supplied (null)
        Result<UserDTO> result = userService.register(null, "password");

        // Post-condition: registration fails with an empty-username error; nothing is saved
        assertFalse(result.isSuccess(), "Post: null username must be rejected");
        assertTrue(result.getErrorMessage().contains("empty") || result.getErrorMessage().contains("null")
                || result.getErrorMessage().contains("Username"),
                "Post: error must indicate the username is invalid");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Given blank username (whitespace) — When registering — Then registration rejected")
    void GivenBlankUsername_WhenRegistering_ThenRejected() {
        // Pre-condition: username is blank (only whitespace)
        Result<UserDTO> result = userService.register("   ", "password");

        // Post-condition: registration fails; blank username is not accepted
        assertFalse(result.isSuccess(), "Post: blank username must be rejected");
        assertTrue(result.getErrorMessage().contains("empty") || result.getErrorMessage().contains("Username"),
                "Post: error must indicate the username is invalid");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Given null password — When registering — Then registration rejected")
    void GivenNullPassword_WhenRegistering_ThenRejected() {
        when(userRepository.findByUsername("carol")).thenReturn(Optional.empty());
        // Pre-condition: valid username but no password supplied
        Result<UserDTO> result = userService.register("carol", null);

        // Post-condition: registration fails with an empty-password error; nothing is saved
        assertFalse(result.isSuccess(), "Post: null password must be rejected");
        assertTrue(result.getErrorMessage().contains("empty") || result.getErrorMessage().contains("Password"),
                "Post: error must indicate the password is invalid");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Given blank password (whitespace) — When registering — Then registration rejected")
    void GivenBlankPassword_WhenRegistering_ThenRejected() {
        when(userRepository.findByUsername("dave")).thenReturn(Optional.empty());
        // Pre-condition: valid unique username but password is blank
        Result<UserDTO> result = userService.register("dave", "   ");

        // Post-condition: registration fails; blank password is not accepted
        assertFalse(result.isSuccess(), "Post: blank password must be rejected");
        assertTrue(result.getErrorMessage().contains("empty") || result.getErrorMessage().contains("Password"),
                "Post: error must indicate the password is invalid");
        verify(userRepository, never()).save(any());
    }
}
