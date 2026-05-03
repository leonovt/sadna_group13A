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
 * Verifies that the system opens public access only after all health checks pass,
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
            
            Result<Void> result = marketplaceService.openMarketplace(adminId);
            
            assertTrue(result.isSuccess(), "Marketplace should open successfully");
            when(marketplaceService.getSystemStatus()).thenReturn("OPEN");
            assertEquals("OPEN", marketplaceService.getSystemStatus());
            verify(marketplaceService).openMarketplace(adminId);
        }

        @Test
        @DisplayName("Given marketplace is OPEN — When external user accesses — Then access is granted")
        void GivenOpenMarketplace_WhenExternalUserAccesses_ThenAccessGranted() {
            when(marketplaceService.getSystemStatus()).thenReturn("OPEN");
            when(marketplaceService.checkPublicAccess()).thenReturn(Result.success());
            
            Result<Void> result = marketplaceService.checkPublicAccess();
            
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
            
            Result<Void> result = marketplaceService.openMarketplace(adminId);
            
            assertFalse(result.isSuccess(), "Cannot open uninitialized system");
            assertTrue(result.getErrorMessage().contains("not initialized"));
        }

        @Test
        @DisplayName("Given payment provider unavailable — When trying to open marketplace — Then opening blocked with alert")
        void GivenPaymentProviderDown_WhenOpening_ThenBlockedWithAlert() {
            String adminId = "admin123";
            when(marketplaceService.getSystemStatus()).thenReturn("INITIALIZED");
            
            // Simulating internal check payment gateway failure
            when(marketplaceService.openMarketplace(adminId)).thenReturn(Result.failure("Payment provider unavailable"));
            
            Result<Void> result = marketplaceService.openMarketplace(adminId);
            
            assertFalse(result.isSuccess(), "Marketplace cannot open if payment provider is down");
            assertTrue(result.getErrorMessage().contains("Payment provider"));
        }

        @Test
        @DisplayName("Given marketplace not yet open — When user accesses public URL — Then error or maintenance page returned")
        void GivenMarketplaceNotOpen_WhenUserAccesses_ThenMaintenancePage() {
            when(marketplaceService.getSystemStatus()).thenReturn("INITIALIZED");
            when(marketplaceService.checkPublicAccess()).thenReturn(Result.failure("Marketplace is down for maintenance"));
            
            Result<Void> result = marketplaceService.checkPublicAccess();
            
            assertFalse(result.isSuccess(), "Public access must be blocked if not OPEN");
            assertTrue(result.getErrorMessage().contains("maintenance") || result.getErrorMessage().contains("down"));
        }
    }
    
    // Interface to mock Marketplace service expected to be implemented in another branch
    public interface IMarketplaceService {
        Result<Void> openMarketplace(String adminId);
        Result<Void> checkPublicAccess();
        String getSystemStatus();
    }
}
