package com.sadna.group13a.acceptance;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Acceptance tests for UC 2.13: Event and Inventory Management.
 *
 * Verifies event CRUD, inventory updates, deletion blocking for sold events,
 * cancellation with automatic refunds, and real-time inventory reflection.
 */
@DisplayName("UC 2.13 — Event and Inventory Management")
class EventManagementTest {

    @Test
    @Disabled("Requires EventAppService + IEventRepository")
    @DisplayName("Given event with existing purchases — When attempting deletion — Then deletion blocked")
    void GivenEventWithPurchases_WhenDeleting_ThenBlocked() {
    }

    @Test
    @Disabled("Requires EventAppService + IPaymentGateway")
    @DisplayName("Given event with purchases needs cancellation — When cancelled — Then full automatic refund to ALL buyers (UC 1.3)")
    void GivenEventWithPurchasesNeedsCancellation_WhenCancelled_ThenFullRefundToAll() {
    }

    @Test
    @Disabled("Requires EventAppService")
    @DisplayName("Given inventory update (e.g., add tickets) — Then reflected in real-time for queued/lottery users")
    void GivenInventoryUpdate_ThenReflectedInRealTime() {
    }

    @Test
    @Disabled("Requires EventAppService + PurchasePolicy")
    @DisplayName("Given inventory change contradicting purchase policy — Then change blocked")
    void GivenInventoryChangeContradictsPolicy_ThenBlocked() {
    }

    @Test
    @Disabled("Requires EventAppService + CompanyRole")
    @DisplayName("Given user without company management permissions — When editing event — Then access denied")
    void GivenUnauthorizedUser_WhenEditingEvent_ThenDenied() {
    }
}
