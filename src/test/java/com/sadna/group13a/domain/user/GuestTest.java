package com.sadna.group13a.domain.user;

import com.sadna.group13a.domain.shared.UserRole;
import com.sadna.group13a.domain.shared.UserState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Guest domain class.
 */
@DisplayName("Guest User Tests")
class GuestTest {

    private Guest guest;

    @BeforeEach
    void setUp() {
        guest = new Guest("guest-1", "visitor");
    }

    @Test
    @DisplayName("Given new guest — When checking role — Then role is GUEST")
    void GivenNewGuest_WhenCheckingRole_ThenRoleIsGuest() {
        assertEquals(UserRole.GUEST, guest.getRole());
    }

    @Test
    @DisplayName("Given guest — When checking authentication — Then is not authenticated")
    void GivenGuest_WhenCheckingAuth_ThenNotAuthenticated() {
        assertFalse(guest.isAuthenticated());
    }

    @Test
    @DisplayName("Given guest — When checking canPurchase — Then cannot purchase")
    void GivenGuest_WhenCheckingCanPurchase_ThenCannotPurchase() {
        assertFalse(guest.canPurchase());
    }

    @Test
    @DisplayName("Given guest — When checking canManageSystem — Then cannot manage")
    void GivenGuest_WhenCheckingCanManage_ThenCannotManage() {
        assertFalse(guest.canManageSystem());
    }

    @Test
    @DisplayName("Given new guest — When created — Then state is ACTIVE")
    void GivenNewGuest_WhenCreated_ThenStateIsActive() {
        assertTrue(guest.isActive());
        assertEquals(UserState.ACTIVE, guest.getState());
    }

    @Test
    @DisplayName("Given guest — When checking active order — Then has no active order")
    void GivenGuest_WhenCheckingActiveOrder_ThenNoActiveOrder() {
        assertFalse(guest.hasActiveOrder());
        assertNull(guest.getActiveOrderId());
    }

    @Test
    @DisplayName("Given guest — When deactivated — Then state is INACTIVE")
    void GivenGuest_WhenDeactivated_ThenStateIsInactive() {
        guest.deactivate();

        assertFalse(guest.isActive());
        assertEquals(UserState.INACTIVE, guest.getState());
    }

    @Test
    @DisplayName("Given guest created with auto ID — When checking ID — Then ID is not null")
    void GivenAutoIdGuest_WhenCheckingId_ThenIdNotNull() {
        Guest autoGuest = new Guest("auto-visitor");

        assertNotNull(autoGuest.getId());
        assertFalse(autoGuest.getId().isEmpty());
    }
}
