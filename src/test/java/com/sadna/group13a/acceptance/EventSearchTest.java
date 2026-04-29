package com.sadna.group13a.acceptance;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Acceptance tests for UC 2.3: Search and Filter Events.
 *
 * Verifies event search by keywords, date, price range, artist;
 * ensures inactive companies are excluded; and scoping by company page.
 */
@DisplayName("UC 2.3 — Search and Filter Events")
class EventSearchTest {

    @Test
    @Disabled("Requires EventAppService + IEventRepository + ICompanyRepository")
    @DisplayName("Given active companies with events — When global search — Then results include events from ALL active companies")
    void GivenActiveCompanies_WhenGlobalSearch_ThenResultsFromAllActive() {
    }

    @Test
    @Disabled("Requires EventAppService + ICompanyRepository")
    @DisplayName("Given inactive company — When global search — Then NO events from inactive company appear")
    void GivenInactiveCompany_WhenGlobalSearch_ThenNoEventsFromInactive() {
    }

    @Test
    @Disabled("Requires EventAppService")
    @DisplayName("Given search from specific company page — Then results limited to that company only, no data leakage")
    void GivenCompanyPageSearch_ThenResultsLimitedToThatCompany() {
    }

    @Test
    @Disabled("Requires EventAppService")
    @DisplayName("Given search with date filter — Then only events matching date range returned")
    void GivenDateFilter_ThenOnlyMatchingDatesReturned() {
    }

    @Test
    @Disabled("Requires EventAppService")
    @DisplayName("Given search with price range — Then only events within price range returned")
    void GivenPriceRange_ThenOnlyMatchingPricesReturned() {
    }

    @Test
    @Disabled("Requires EventAppService")
    @DisplayName("Given search with artist name — Then only events featuring that artist returned")
    void GivenArtistName_ThenOnlyMatchingArtistEventsReturned() {
    }
}
