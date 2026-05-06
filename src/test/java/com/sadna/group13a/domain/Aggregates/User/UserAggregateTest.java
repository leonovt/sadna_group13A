package com.sadna.group13a.domain.Aggregates.User;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;


class UserAggregateTest {

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
