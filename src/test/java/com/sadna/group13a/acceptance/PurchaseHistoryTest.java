package com.sadna.group13a.acceptance;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Acceptance tests for UC 2.11: View Personal Purchase History.
 *
 * Verifies that history shows accurate snapshot data, blocks cross-user access,
 * and correctly marks refunded tickets.
 */
@DisplayName("UC 2.11 — Personal Purchase History")
class PurchaseHistoryTest {

    @Test
    @Disabled("Requires HistoryAppService + IHistoryRepository")
    @DisplayName("Given completed purchases — Then history shows exact price and event details as at time of purchase, even if event cancelled or price changed since")
    void GivenCompletedPurchases_ThenHistoryShowsOriginalSnapshotData() {
    }

    @Test
    @Disabled("Requires HistoryAppService")
    @DisplayName("Given authenticated member — Then can only view OWN purchase history, not another member's")
    void GivenAuthenticatedMember_ThenCanOnlyViewOwnHistory() {
    }

    @Test
    @Disabled("Requires HistoryAppService")
    @DisplayName("Given unauthenticated user — When accessing history — Then access denied with login required")
    void GivenUnauthenticatedUser_WhenAccessingHistory_ThenAccessDenied() {
    }

    @Test
    @Disabled("Requires HistoryAppService")
    @DisplayName("Given tickets refunded via UC 1.3 — Then history clearly shows refund status")
    void GivenRefundedTickets_ThenHistoryClearlyShowsRefundStatus() {
    }
}
