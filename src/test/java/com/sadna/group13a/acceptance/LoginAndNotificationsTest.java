package com.sadna.group13a.acceptance;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Acceptance tests for UC 2.2: Authenticated Login and Notification Handling.
 *
 * Verifies login flow, session creation, deferred notification delivery,
 * and error handling for invalid credentials.
 */
@DisplayName("UC 2.2 — Authenticated Login and Notification Handling")
class LoginAndNotificationsTest {

    @Nested
    @DisplayName("Successful Login")
    class SuccessScenarios {

        @Test
        @Disabled("Requires AuthAppService")
        @DisplayName("Given valid credentials — When member logs in — Then session created and status changed to MEMBER")
        void GivenValidCredentials_WhenLogin_ThenSessionCreatedAndStatusMember() {
            // Arrange: registered member with known credentials
            // Act: AuthAppService.login(username, password)
            // Assert: SessionToken returned, user.isAuthenticated() == true
        }

        @Test
        @Disabled("Requires AuthAppService + NotificationService")
        @DisplayName("Given member with pending notifications — When login — Then all deferred notifications returned immediately")
        void GivenPendingNotifications_WhenLogin_ThenDeferredNotificationsReturned() {
            // Arrange: member has pending notifications (refund, lottery win, queue update)
            // Act: login
            // Assert: all pending notifications returned in response, marked as SENT
        }

        @Test
        @Disabled("Requires AuthAppService")
        @DisplayName("Given successful login — Then system displays username and stops showing login/register prompts")
        void GivenSuccessfulLogin_ThenUsernameDisplayedAndPromptsHidden() {
        }
    }

    @Nested
    @DisplayName("Login Failures")
    class FailureScenarios {

        @Test
        @Disabled("Requires AuthAppService")
        @DisplayName("Given wrong password — When login — Then error returned and user stays as GUEST without personal data access")
        void GivenWrongPassword_WhenLogin_ThenErrorAndStaysGuest() {
            // Assert: AuthenticationException thrown
            // Assert: user remains GUEST, no personal data exposed
        }

        @Test
        @Disabled("Requires AuthAppService")
        @DisplayName("Given nonexistent username — When login — Then error returned")
        void GivenNonexistentUsername_WhenLogin_ThenError() {
        }

        @Test
        @Disabled("Requires AuthAppService")
        @DisplayName("Given deactivated account — When login — Then error returned")
        void GivenDeactivatedAccount_WhenLogin_ThenError() {
        }
    }
}
