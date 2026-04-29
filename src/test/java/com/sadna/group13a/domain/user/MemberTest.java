package com.sadna.group13a.domain.user;

import com.sadna.group13a.domain.shared.AuthenticationException;
import com.sadna.group13a.domain.shared.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Member domain class.
 * Covers authentication, session management, and permissions.
 */
@DisplayName("Member User Tests")
class MemberTest {

    private Member member;

    @BeforeEach
    void setUp() {
        member = new Member("member-1", "john_doe", "hashed_password_123");
    }

    // ── Role & Identity ───────────────────────────────────────────

    @Test
    @DisplayName("Given new member — When checking role — Then role is MEMBER")
    void GivenNewMember_WhenCheckingRole_ThenRoleIsMember() {
        assertEquals(UserRole.MEMBER, member.getRole());
    }

    @Test
    @DisplayName("Given new member — When checking password hash — Then hash is stored")
    void GivenNewMember_WhenCheckingPasswordHash_ThenHashIsStored() {
        assertEquals("hashed_password_123", member.getPasswordHash());
    }

    // ── Authentication & Session ──────────────────────────────────

    @Nested
    @DisplayName("Login Scenarios")
    class LoginTests {

        @Test
        @DisplayName("Given active member — When login with valid token — Then is authenticated")
        void GivenActiveMember_WhenLogin_ThenIsAuthenticated() {
            SessionToken token = createValidToken();

            member.login(token);

            assertTrue(member.isAuthenticated());
            assertNotNull(member.getSessionToken());
        }

        @Test
        @DisplayName("Given inactive member — When login — Then throws AuthenticationException")
        void GivenInactiveMember_WhenLogin_ThenThrowsException() {
            member.deactivate();
            SessionToken token = createValidToken();

            assertThrows(AuthenticationException.class, () -> member.login(token));
        }

        @Test
        @DisplayName("Given member not logged in — When checking auth — Then not authenticated")
        void GivenMemberNotLoggedIn_WhenCheckingAuth_ThenNotAuthenticated() {
            assertFalse(member.isAuthenticated());
        }

        @Test
        @DisplayName("Given member with expired token — When checking auth — Then not authenticated")
        void GivenMemberWithExpiredToken_WhenCheckingAuth_ThenNotAuthenticated() {
            SessionToken expiredToken = createExpiredToken();
            member.login(expiredToken); // login while token was valid at creation time

            // Token has expired
            assertFalse(member.isAuthenticated());
        }
    }

    @Nested
    @DisplayName("Logout Scenarios")
    class LogoutTests {

        @Test
        @DisplayName("Given logged-in member — When logout — Then is not authenticated")
        void GivenLoggedInMember_WhenLogout_ThenNotAuthenticated() {
            member.login(createValidToken());
            assertTrue(member.isAuthenticated());

            member.logout();

            assertFalse(member.isAuthenticated());
            assertNull(member.getSessionToken());
        }

        @Test
        @DisplayName("Given member not logged in — When logout — Then no error and still not authenticated")
        void GivenNotLoggedInMember_WhenLogout_ThenNoError() {
            assertDoesNotThrow(() -> member.logout());
            assertFalse(member.isAuthenticated());
        }
    }

    // ── Permissions ───────────────────────────────────────────────

    @Nested
    @DisplayName("Permission Scenarios")
    class PermissionTests {

        @Test
        @DisplayName("Given authenticated active member — When checking canPurchase — Then can purchase")
        void GivenAuthenticatedActiveMember_WhenCanPurchase_ThenTrue() {
            member.login(createValidToken());

            assertTrue(member.canPurchase());
        }

        @Test
        @DisplayName("Given unauthenticated member — When checking canPurchase — Then cannot purchase")
        void GivenUnauthenticatedMember_WhenCanPurchase_ThenFalse() {
            assertFalse(member.canPurchase());
        }

        @Test
        @DisplayName("Given inactive member — When checking canPurchase — Then cannot purchase")
        void GivenInactiveMember_WhenCanPurchase_ThenFalse() {
            member.login(createValidToken());
            member.deactivate();

            assertFalse(member.canPurchase());
        }

        @Test
        @DisplayName("Given member — When checking canManageSystem — Then cannot manage system")
        void GivenMember_WhenCanManageSystem_ThenFalse() {
            member.login(createValidToken());
            assertFalse(member.canManageSystem());
        }
    }

    // ── Password Update ───────────────────────────────────────────

    @Test
    @DisplayName("Given member — When changing password hash — Then new hash is stored")
    void GivenMember_WhenChangingPasswordHash_ThenNewHashStored() {
        member.setPasswordHash("new_hashed_password");

        assertEquals("new_hashed_password", member.getPasswordHash());
    }

    // ── Helpers ───────────────────────────────────────────────────

    private SessionToken createValidToken() {
        Instant now = Instant.now();
        return new SessionToken("valid-jwt-token", now, now.plus(1, ChronoUnit.HOURS));
    }

    private SessionToken createExpiredToken() {
        Instant past = Instant.now().minus(2, ChronoUnit.HOURS);
        Instant expired = Instant.now().minus(1, ChronoUnit.HOURS);
        return new SessionToken("expired-jwt-token", past, expired);
    }
}
