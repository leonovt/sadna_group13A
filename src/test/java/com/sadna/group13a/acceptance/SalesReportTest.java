package com.sadna.group13a.acceptance;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Acceptance tests for UC 2.15: Generate Sales Report.
 *
 * Verifies hierarchical data security, data accuracy,
 * and consistency with actual payment gateway charges.
 */
@DisplayName("UC 2.15 — Sales Report Generation")
class SalesReportTest {

    @Test
    @Disabled("Requires CompanyAppService + IHistoryRepository")
    @DisplayName("Given manager — When generating report — Then report includes ONLY events in their sub-tree, not events outside their hierarchy")
    void GivenManager_WhenGeneratingReport_ThenOnlySubTreeData() {
        // Hierarchical data security: owner sees only data from people they appointed (and their appointees)
    }

    @Test
    @Disabled("Requires CompanyAppService + IHistoryRepository")
    @DisplayName("Given report generated — Then only includes transactions with status PAID (not cancelled or pending)")
    void GivenReportGenerated_ThenOnlyPaidTransactions() {
    }

    @Test
    @Disabled("Requires CompanyAppService + IPaymentGateway")
    @DisplayName("Given report total — Then matches exactly the amounts charged by payment provider minus refunds")
    void GivenReportTotal_ThenMatchesPaymentProviderChargesMinusRefunds() {
    }

    @Test
    @Disabled("Requires CompanyAppService")
    @DisplayName("Given unauthorized user — When requesting report — Then access denied")
    void GivenUnauthorizedUser_WhenRequestingReport_ThenDenied() {
    }
}
