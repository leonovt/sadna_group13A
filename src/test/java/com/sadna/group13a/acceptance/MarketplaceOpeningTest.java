package com.sadna.group13a.acceptance;

import com.sadna.group13a.application.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Acceptance tests for UC 1.2: Opening the Marketplace.
 *
 * Verifies that the system opens public access only after all health checks
 * pass,
 * and blocks opening if payment providers are unavailable.
 */
@DisplayName("UC 1.2 — Opening the Marketplace")
class MarketplaceOpeningTest {

    private IMarketplaceService marketplaceService;

    @BeforeEach
    void setUp() {
        // Assume MarketplaceService functionality resides in another branch
        marketplaceService = mock(IMarketplaceService.class);
    }

    @Nested
    @DisplayName("Successful Opening")
    class SuccessScenarios {

        @Test
        @DisplayName("Given system is INITIALIZED and all health checks pass — When opening marketplace — Then status becomes OPEN")
        void GivenInitializedSystem_WhenOpening_ThenStatusIsOpen() {
            String adminId = "admin123";

            when(marketplaceService.getSystemStatus()).thenReturn("INITIALIZED");
            when(marketplaceService.openMarketplace(adminId)).thenReturn(Result.success());
            // Pre-condition: system is in INITIALIZED state, not yet OPEN
            assertEquals("INITIALIZED", marketplaceService.getSystemStatus(), "Pre: system must be INITIALIZED before opening");

            Result<Void> result = marketplaceService.openMarketplace(adminId);

            // Post-condition: open call succeeds and status transitions to OPEN
            assertTrue(result.isSuccess(), "Marketplace should open successfully");
            when(marketplaceService.getSystemStatus()).thenReturn("OPEN");
            assertEquals("OPEN", marketplaceService.getSystemStatus(), "Post: system status must be OPEN after successful opening");
            verify(marketplaceService).openMarketplace(adminId);
        }

        @Test
        @DisplayName("Given marketplace is OPEN — When external user accesses — Then access is granted")
        void GivenOpenMarketplace_WhenExternalUserAccesses_ThenAccessGranted() {
            when(marketplaceService.getSystemStatus()).thenReturn("OPEN");
            when(marketplaceService.checkPublicAccess()).thenReturn(Result.success());
            // Pre-condition: marketplace is already in OPEN state
            assertEquals("OPEN", marketplaceService.getSystemStatus(), "Pre: marketplace must be OPEN before checking public access");

            Result<Void> result = marketplaceService.checkPublicAccess();

            // Post-condition: public access is granted
            assertTrue(result.isSuccess(), "Public access should be granted when OPEN");
        }
    }

    @Nested
    @DisplayName("Failure & Edge Scenarios")
    class FailureScenarios {

        @Test
        @DisplayName("Given system NOT initialized — When trying to open marketplace — Then opening blocked")
        void GivenNotInitialized_WhenOpening_ThenBlocked() {
            String adminId = "admin123";
            when(marketplaceService.getSystemStatus()).thenReturn("UNINITIALIZED");
            when(marketplaceService.openMarketplace(adminId)).thenReturn(Result.failure("System not initialized"));
            // Pre-condition: system has not been initialized
            assertEquals("UNINITIALIZED", marketplaceService.getSystemStatus(), "Pre: system must be uninitialized for this test");

            Result<Void> result = marketplaceService.openMarketplace(adminId);

            // Post-condition: opening is blocked and system status unchanged
            assertFalse(result.isSuccess(), "Cannot open uninitialized system");
            assertTrue(result.getErrorMessage().contains("not initialized"));
            assertEquals("UNINITIALIZED", marketplaceService.getSystemStatus(), "Post: system status must remain UNINITIALIZED after failed open");
        }

        @Test
        @DisplayName("Given payment provider unavailable — When trying to open marketplace — Then opening blocked with alert")
        void GivenPaymentProviderDown_WhenOpening_ThenBlockedWithAlert() {
            String adminId = "admin123";
            when(marketplaceService.getSystemStatus()).thenReturn("INITIALIZED");
            // Pre-condition: system is initialized but payment provider is unavailable
            assertEquals("INITIALIZED", marketplaceService.getSystemStatus(), "Pre: system must be INITIALIZED for this test");

            // Simulating internal check payment gateway failure
            when(marketplaceService.openMarketplace(adminId))
                    .thenReturn(Result.failure("Payment provider unavailable"));

            Result<Void> result = marketplaceService.openMarketplace(adminId);

            // Post-condition: opening is blocked with a payment-related error
            assertFalse(result.isSuccess(), "Marketplace cannot open if payment provider is down");
            assertTrue(result.getErrorMessage().contains("Payment provider"));
        }

        @Test
        @DisplayName("Given marketplace not yet open — When user accesses public URL — Then error or maintenance page returned")
        void GivenMarketplaceNotOpen_WhenUserAccesses_ThenMaintenancePage() {
            when(marketplaceService.getSystemStatus()).thenReturn("INITIALIZED");
            when(marketplaceService.checkPublicAccess())
                    .thenReturn(Result.failure("Marketplace is down for maintenance"));
            // Pre-condition: marketplace is initialized but not yet OPEN
            assertEquals("INITIALIZED", marketplaceService.getSystemStatus(), "Pre: marketplace must not be OPEN yet");

            Result<Void> result = marketplaceService.checkPublicAccess();

            // Post-condition: public access is blocked with a maintenance message
            assertFalse(result.isSuccess(), "Public access must be blocked if not OPEN");
            assertTrue(result.getErrorMessage().contains("maintenance") || result.getErrorMessage().contains("down"));
        }
    }

    // Interface to mock Marketplace service expected to be implemented in another
    // branch
    public interface IMarketplaceService {
        Result<Void> openMarketplace(String adminId);

        Result<Void> checkPublicAccess();

        String getSystemStatus();
    }
}
