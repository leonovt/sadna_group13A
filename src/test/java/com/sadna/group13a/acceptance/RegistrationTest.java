package com.sadna.group13a.acceptance;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Acceptance tests for UC 2.1: Registration to Platform.
 *
 * Verifies guest registration flow: validation, uniqueness, password encryption,
 * and that the user remains a Guest until explicit login.
 */
@DisplayName("UC 2.1 — Registration to Platform")
class RegistrationTest {

    @Nested
    @DisplayName("Successful Registration")
    class SuccessScenarios {

        @Test
        @Disabled("Requires AuthAppService + IUserRepository")
        @DisplayName("Given valid unique details — When guest registers — Then user created as MEMBER in repository")
        void GivenValidDetails_WhenGuestRegisters_ThenMemberCreated() {
            // Arrange: unique username, valid email, valid password
            // Act: AuthAppService.register(username, password, email, fullName)
            // Assert: IUserRepository.findByUsername(username) returns Member
        }

        @Test
        @Disabled("Requires AuthAppService")
        @DisplayName("Given successful registration — Then password stored encrypted, NOT as plain text")
        void GivenSuccessfulRegistration_ThenPasswordEncrypted() {
            // Assert: stored passwordHash != original plain text password
            // Assert: BCrypt.matches(plainPassword, storedHash) == true
        }

        @Test
        @Disabled("Requires AuthAppService")
        @DisplayName("Given successful registration — Then user remains GUEST status until explicit login")
        void GivenSuccessfulRegistration_ThenUserRemainsGuestUntilLogin() {
            // Assert: user is not authenticated after registration
            // Assert: user cannot access member-only areas
        }
    }

    @Nested
    @DisplayName("Registration Failures")
    class FailureScenarios {

        @Test
        @Disabled("Requires AuthAppService + IUserRepository")
        @DisplayName("Given username already taken — When guest registers — Then registration rejected with error")
        void GivenUsernameTaken_WhenRegistering_ThenRejected() {
            // Arrange: "john_doe" already exists
            // Act: attempt register with "john_doe"
            // Assert: error message about duplicate username
        }

        @Test
        @Disabled("Requires AuthAppService")
        @DisplayName("Given invalid email format — When guest registers — Then registration rejected")
        void GivenInvalidEmail_WhenRegistering_ThenRejected() {
        }

        @Test
        @Disabled("Requires AuthAppService")
        @DisplayName("Given empty required fields — When guest registers — Then registration rejected")
        void GivenEmptyFields_WhenRegistering_ThenRejected() {
        }
    }
}
