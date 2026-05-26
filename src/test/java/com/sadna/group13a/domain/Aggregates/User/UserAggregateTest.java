package com.sadna.group13a.domain.Aggregates.User;

import com.sadna.group13a.domain.Aggregates.Company.CompanyRole;
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

    // ── Member: password ──────────────────────────────────────────

    @Test
    void givenMember_whenGetHashedPassword_thenReturnsConstructorHash() {
        Member member = new Member(UUID.randomUUID().toString(), "alice", "my_hash");

        assertEquals("my_hash", member.getHashedPassword());
    }

    @Test
    void givenMember_whenSetPasswordHash_thenHashUpdatesAndVersionIncrements() {
        Member member = new Member(UUID.randomUUID().toString(), "alice", "old_hash");
        int vBefore = member.getVersion();

        member.setPasswordHash("new_hash");

        assertEquals("new_hash", member.getHashedPassword());
        assertTrue(member.getVersion() > vBefore);
    }

    // ── Member: company roles ─────────────────────────────────────

    @Test
    void givenMember_whenAddCompanyRole_thenRoleAndAppointedByAreStored() {
        Member member = new Member(UUID.randomUUID().toString(), "alice", "pw");

        member.addCompanyRole("co-1", CompanyRole.MANAGER, "founder-1");

        assertEquals(CompanyRole.MANAGER, member.getRoleInCompany("co-1"));
        assertEquals("founder-1", member.getAppointedByInCompany("co-1"));
        assertTrue(member.getCompanyRoles().containsKey("co-1"));
    }

    @Test
    void givenMemberWithRole_whenRemoveCompanyRole_thenRoleIsGone() {
        Member member = new Member(UUID.randomUUID().toString(), "alice", "pw");
        member.addCompanyRole("co-1", CompanyRole.OWNER, null);

        member.removeCompanyRole("co-1");

        assertNull(member.getRoleInCompany("co-1"));
        assertFalse(member.getCompanyRoles().containsKey("co-1"));
    }

    // ── Invariants: type state is independent of active/inactive ──

    @Test
    void givenActiveMember_whenDeactivated_thenRoleIsStillMemberAndPasswordStillAccessible() {
        Member member = new Member(UUID.randomUUID().toString(), "alice", "pw");

        member.deactivate();

        assertEquals(UserRole.MEMBER, member.getRole());
        assertEquals("pw", member.getHashedPassword());
        assertFalse(member.isActive());
        assertFalse(member.canPurchase());
    }

    @Test
    void givenGuest_whenDeactivated_thenRoleIsStillGuestAndCannotPurchase() {
        Guest guest = new Guest(UUID.randomUUID().toString(), "guest-1");

        guest.deactivate();

        assertEquals(UserRole.GUEST, guest.getRole());
        assertNull(guest.getHashedPassword());
        assertFalse(guest.canPurchase());
        assertFalse(guest.canManageSystem());
    }

    // ── Invariants: version tracks every mutation ─────────────────

    @Test
    void givenMember_whenMutatedMultipleTimes_thenVersionIncrementsEachTime() {
        Member member = new Member(UUID.randomUUID().toString(), "alice", "pw");
        int v0 = member.getVersion();

        member.setUsername("bob");
        int v1 = member.getVersion();

        member.deactivate();
        int v2 = member.getVersion();

        member.setActiveOrderId("order-1");
        int v3 = member.getVersion();

        assertTrue(v1 > v0);
        assertTrue(v2 > v1);
        assertTrue(v3 > v2);
    }
}
