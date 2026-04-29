package com.sadna.group13a.domain.user;

import com.sadna.group13a.domain.shared.AuthenticationException;
import com.sadna.group13a.domain.shared.UserRole;
import com.sadna.group13a.domain.shared.UserState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Admin domain class.
 * Covers authentication, system management permissions, and user management operations.
 */
@DisplayName("Admin User Tests")
class AdminTest {

    private Admin admin;

    @BeforeEach
    void setUp() {
        admin = new Admin("admin-1", "sys_admin", "hashed_admin_pass");
    }

    // ── Role ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Given new admin — When checking role — Then role is ADMIN")
    void GivenNewAdmin_WhenCheckingRole_ThenRoleIsAdmin() {
        assertEquals(UserRole.ADMIN, admin.getRole());
    }

    // ── Authentication ────────────────────────────────────────────

    @Nested
    @DisplayName("Admin Login Scenarios")
    class LoginTests {

        @Test
        @DisplayName("Given active admin — When login — Then is authenticated")
        void GivenActiveAdmin_WhenLogin_ThenIsAuthenticated() {
            admin.login(createValidToken());

            assertTrue(admin.isAuthenticated());
        }

        @Test
        @DisplayName("Given inactive admin — When login — Then throws AuthenticationException")
        void GivenInactiveAdmin_WhenLogin_ThenThrowsException() {
            admin.deactivate();

            assertThrows(AuthenticationException.class,
                    () -> admin.login(createValidToken()));
        }

        @Test
        @DisplayName("Given admin — When logout — Then is not authenticated")
        void GivenAdmin_WhenLogout_ThenNotAuthenticated() {
            admin.login(createValidToken());
            admin.logout();

            assertFalse(admin.isAuthenticated());
        }
    }

    // ── Permissions ───────────────────────────────────────────────

    @Nested
    @DisplayName("Admin Permission Scenarios")
    class PermissionTests {

        @Test
        @DisplayName("Given authenticated admin — When checking canPurchase — Then can purchase")
        void GivenAuthenticatedAdmin_WhenCanPurchase_ThenTrue() {
            admin.login(createValidToken());

            assertTrue(admin.canPurchase());
        }

        @Test
        @DisplayName("Given authenticated admin — When checking canManageSystem — Then can manage")
        void GivenAuthenticatedAdmin_WhenCanManageSystem_ThenTrue() {
            admin.login(createValidToken());

            assertTrue(admin.canManageSystem());
        }

        @Test
        @DisplayName("Given unauthenticated admin — When checking canManageSystem — Then cannot manage")
        void GivenUnauthenticatedAdmin_WhenCanManageSystem_ThenFalse() {
            assertFalse(admin.canManageSystem());
        }

        @Test
        @DisplayName("Given inactive admin — When checking canManageSystem — Then cannot manage")
        void GivenInactiveAdmin_WhenCanManageSystem_ThenFalse() {
            admin.login(createValidToken());
            admin.deactivate();

            assertFalse(admin.canManageSystem());
        }
    }

    // ── User Management (Domain operations) ───────────────────────

    @Nested
    @DisplayName("Admin User Management Scenarios")
    class UserManagementTests {

        @Test
        @DisplayName("Given authenticated admin — When deactivating member — Then member is inactive")
        void GivenAuthenticatedAdmin_WhenDeactivatingMember_ThenMemberInactive() {
            admin.login(createValidToken());
            Member member = new Member("m-1", "john", "pass");
            assertTrue(member.isActive());

            admin.deactivateUser(member);

            assertFalse(member.isActive());
            assertEquals(UserState.INACTIVE, member.getState());
        }

        @Test
        @DisplayName("Given authenticated admin — When activating inactive member — Then member is active")
        void GivenAuthenticatedAdmin_WhenActivatingMember_ThenMemberActive() {
            admin.login(createValidToken());
            Member member = new Member("m-1", "john", "pass");
            member.deactivate();

            admin.activateUser(member);

            assertTrue(member.isActive());
        }

        @Test
        @DisplayName("Given unauthenticated admin — When deactivating member — Then throws exception")
        void GivenUnauthenticatedAdmin_WhenDeactivatingMember_ThenThrowsException() {
            Member member = new Member("m-1", "john", "pass");

            assertThrows(AuthenticationException.class,
                    () -> admin.deactivateUser(member));
        }

        @Test
        @DisplayName("Given unauthenticated admin — When activating member — Then throws exception")
        void GivenUnauthenticatedAdmin_WhenActivatingMember_ThenThrowsException() {
            Member member = new Member("m-1", "john", "pass");
            member.deactivate();

            assertThrows(AuthenticationException.class,
                    () -> admin.activateUser(member));
        }

        @Test
        @DisplayName("Given authenticated admin — When deactivating another admin — Then target admin is inactive")
        void GivenAuthenticatedAdmin_WhenDeactivatingAnotherAdmin_ThenTargetInactive() {
            admin.login(createValidToken());
            Admin otherAdmin = new Admin("a-2", "other_admin", "pass");

            admin.deactivateUser(otherAdmin);

            assertFalse(otherAdmin.isActive());
        }

        @Test
        @DisplayName("Given authenticated admin — When deactivating guest — Then guest is inactive")
        void GivenAuthenticatedAdmin_WhenDeactivatingGuest_ThenGuestInactive() {
            admin.login(createValidToken());
            Guest guest = new Guest("g-1", "visitor");

            admin.deactivateUser(guest);

            assertFalse(guest.isActive());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────

    private SessionToken createValidToken() {
        Instant now = Instant.now();
        return new SessionToken("admin-jwt-token", now, now.plus(1, ChronoUnit.HOURS));
    }
}
