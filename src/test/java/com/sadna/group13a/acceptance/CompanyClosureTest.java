package com.sadna.group13a.acceptance;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Acceptance tests for UC 2.16: Close or Suspend Production Company (by Founder).
 *
 * Verifies that only the founder can close the company, events disappear from search,
 * ticket sales are blocked, and all staff are notified.
 */
@DisplayName("UC 2.16 — Company Closure (by Founder)")
class CompanyClosureTest {

    @Test
    @Disabled("Requires CompanyAppService + ICompanyRepository")
    @DisplayName("Given company closed — Then all events disappear from search engine (UC 2.3)")
    void GivenCompanyClosed_ThenEventsDisappearFromSearch() {
    }

    @Test
    @Disabled("Requires CompanyAppService")
    @DisplayName("Given company closed — Then ticket purchases, queue entry, and lottery registration ALL blocked for company events")
    void GivenCompanyClosed_ThenAllPurchaseFlowsBlocked() {
    }

    @Test
    @Disabled("Requires CompanyAppService + CompanyRole")
    @DisplayName("Given non-founder owner — When attempting to close company — Then action blocked and option not visible in UI")
    void GivenNonFounder_WhenClosing_ThenBlocked() {
    }

    @Test
    @Disabled("Requires CompanyAppService + NotificationService")
    @DisplayName("Given company closed — Then all managers and owners receive notification (real-time or deferred)")
    void GivenCompanyClosed_ThenAllStaffNotified() {
    }

    @Test
    @Disabled("Requires CompanyAppService")
    @DisplayName("Given wrong password on closure confirmation — Then closure denied")
    void GivenWrongPassword_ThenClosureDenied() {
    }
}
