package com.sadna.group13a.domain.Aggregates.User;

import com.sadna.group13a.domain.shared.AuthenticationException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the User aggregate (Admin, Member, Guest).
 * No Spring, no Mockito — pure domain instantiation.
 */
class UserAggregateTest {

    // ── Admin: Construction & Role ────────────────────────────────

    @Test
    void givenValidParams_whenCreatingAdmin_thenRoleIsAdminAndStateIsActive() {
        Admin admin = new Admin(UUID.randomUUID().toString(), "sysadmin", "hashed_pw");

        assertEquals(UserRole.ADMIN, admin.getRole());
        assertEquals("sysadmin", admin.getUsername());
        assertEquals("hashed_pw", admin.getHashedPassword());
        assertTrue(admin.isActive());
    }

    @Test
    void givenActiveAdmin_whenCheckingPermissions_thenCanPurchaseAndCanManageSystem() {
        Admin admin = new Admin(UUID.randomUUID().toString(), "admin", "pw");

        assertTrue(admin.canPurchase());
        assertTrue(admin.canManageSystem());
    }

    @Test
    void givenInactiveAdmin_whenCheckingPermissions_thenCannotPurchaseOrManageSystem() {
        Admin admin = new Admin(UUID.randomUUID().toString(), "admin", "pw");
        admin.deactivate();

        assertFalse(admin.canPurchase());
        assertFalse(admin.canManageSystem());
    }

    // ── Admin: Deactivate / Activate target user ──────────────────

    @Test
    void givenActiveAdmin_whenDeactivatingMember_thenMemberBecomesInactive() {
        Admin admin = new Admin(UUID.randomUUID().toString(), "admin", "pw");
        Member target = new Member(UUID.randomUUID().toString(), "alice", "pw");

        admin.deactivateUser(target);

        assertFalse(target.isActive());
    }

    @Test
    void givenActiveAdmin_whenActivatingInactiveMember_thenMemberBecomesActive() {
        Admin admin = new Admin(UUID.randomUUID().toString(), "admin", "pw");
        Member target = new Member(UUID.randomUUID().toString(), "bob", "pw");
        target.deactivate();

        admin.activateUser(target);

        assertTrue(target.isActive());
    }

    @Test
    void givenInactiveAdmin_whenDeactivatingMember_thenThrowsAuthenticationException() {
        Admin admin = new Admin(UUID.randomUUID().toString(), "admin", "pw");
        admin.deactivate();
        Member target = new Member(UUID.randomUUID().toString(), "carol", "pw");

        assertThrows(AuthenticationException.class, () -> admin.deactivateUser(target));
    }

    @Test
    void givenInactiveAdmin_whenActivatingMember_thenThrowsAuthenticationException() {
        Admin admin = new Admin(UUID.randomUUID().toString(), "admin", "pw");
        admin.deactivate();
        Member target = new Member(UUID.randomUUID().toString(), "dave", "pw");
        target.deactivate();

        assertThrows(AuthenticationException.class, () -> admin.activateUser(target));
    }

    // ── Member: Construction & Role ───────────────────────────────

    @Test
    void givenValidParams_whenCreatingMember_thenRoleIsMemberAndStateIsActive() {
        Member member = new Member(UUID.randomUUID().toString(), "alice", "hashed");

        assertEquals(UserRole.MEMBER, member.getRole());
        assertEquals("alice", member.getUsername());
        assertTrue(member.isActive());
    }

    @Test
    void givenActiveMember_whenCheckingPermissions_thenCanPurchaseButCannotManageSystem() {
        Member member = new Member(UUID.randomUUID().toString(), "alice", "pw");

        assertTrue(member.canPurchase());
        assertFalse(member.canManageSystem());
    }

    @Test
    void givenInactiveMember_whenCheckingPermissions_thenCannotPurchase() {
        Member member = new Member(UUID.randomUUID().toString(), "alice", "pw");
        member.deactivate();

        assertFalse(member.canPurchase());
    }

    // ── Guest: Construction & Role ────────────────────────────────

    @Test
    void givenGuestUser_whenCheckingRole_thenRoleIsGuestAndHasNoPassword() {
        Guest guest = new Guest(UUID.randomUUID().toString(), "guest_session");

        assertEquals(UserRole.GUEST, guest.getRole());
        assertNull(guest.getHashedPassword());
        assertFalse(guest.canPurchase());
        assertFalse(guest.canManageSystem());
    }

    // ── User base: state, active order pointer ────────────────────

    @Test
    void givenActiveUser_whenDeactivated_thenStateIsInactive() {
        Member member = new Member(UUID.randomUUID().toString(), "eve", "pw");
        assertTrue(member.isActive());

        member.deactivate();

        assertFalse(member.isActive());
        assertEquals(UserState.INACTIVE, member.getState());
    }

    @Test
    void givenInactiveUser_whenActivated_thenStateIsActive() {
        Member member = new Member(UUID.randomUUID().toString(), "frank", "pw");
        member.deactivate();

        member.activate();

        assertTrue(member.isActive());
        assertEquals(UserState.ACTIVE, member.getState());
    }

    @Test
    void givenNewUser_whenSettingActiveOrderId_thenPointerIsStored() {
        Member member = new Member(UUID.randomUUID().toString(), "grace", "pw");
        String orderId = UUID.randomUUID().toString();

        assertFalse(member.hasActiveOrder());
        member.setActiveOrderId(orderId);

        assertTrue(member.hasActiveOrder());
        assertEquals(orderId, member.getActiveOrderId());
    }

    @Test
    void givenNewUser_whenUpdatingUsername_thenUsernameChanges() {
        Member member = new Member(UUID.randomUUID().toString(), "old_name", "pw");

        member.setUsername("new_name");

        assertEquals("new_name", member.getUsername());
    }
}
