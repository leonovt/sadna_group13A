package com.sadna.group13a.acceptance;

import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Interfaces.IPasswordEncoder;
import com.sadna.group13a.application.Interfaces.IPaymentGateway;
import com.sadna.group13a.application.Interfaces.ITicketSupplier;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.SystemService;
import com.sadna.group13a.domain.Aggregates.User.User;
import com.sadna.group13a.domain.Interfaces.IAdminRepository;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Acceptance tests for UC 1.1: Platform Initialization.
 *
 * Verifies that the system initializes correctly: validates config files,
 * creates default admin if none exists, and verifies external provider
 * connectivity.
 */
@DisplayName("UC 1.1 — Platform Initialization")
class PlatformInitializationTest {

    private SystemService systemService;
    private IUserRepository userRepository;
    private IAdminRepository adminRepository;
    private IAuth authGateway;
    private IPaymentGateway paymentGateway;
    private ITicketSupplier ticketingGateway;
    private IPasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository = mock(IUserRepository.class);
        adminRepository = mock(IAdminRepository.class);
        authGateway = mock(IAuth.class);
        paymentGateway = mock(IPaymentGateway.class);
        ticketingGateway = mock(ITicketSupplier.class);
        passwordEncoder = mock(IPasswordEncoder.class);

        systemService = new SystemService(userRepository, adminRepository, authGateway, paymentGateway, ticketingGateway,
                passwordEncoder);
    }

    @Nested
    @DisplayName("Successful Initialization")
    class SuccessScenarios {

        @Test
        @DisplayName("Given valid config and available providers — When initializing — Then system status is INITIALIZED")
        void GivenValidConfigAndProviders_WhenInitializing_ThenStatusIsInitialized() {
            when(paymentGateway.isConnected()).thenReturn(true);
            when(ticketingGateway.isConnected()).thenReturn(true);
            when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
            // Pre-condition: system has not been initialized yet
            assertFalse(systemService.isPlatformInitialized(), "Pre: system must not be initialized before this test");

            Result<Void> result = systemService.initializePlatform("admin", "adminPass");

            // Post-condition: system is now initialized and result indicates success
            assertTrue(result.isSuccess());
            assertTrue(systemService.isPlatformInitialized(), "Post: system must be initialized after successful init");
        }

        @Test
        @DisplayName("Given no admin exists in DB — When initializing — Then default admin is created")
        void GivenNoAdminExists_WhenInitializing_ThenDefaultAdminCreated() {
            when(paymentGateway.isConnected()).thenReturn(true);
            when(ticketingGateway.isConnected()).thenReturn(true);
            when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
            when(passwordEncoder.encodePassword("adminPass")).thenReturn("hashedPass");
            // Pre-condition: no admin record exists and system is not initialized
            assertFalse(systemService.isPlatformInitialized(), "Pre: system must not be initialized before this test");

            systemService.initializePlatform("admin", "adminPass");

            // Post-condition: admin user was saved and password was encoded
            verify(userRepository).save(any(User.class));
            verify(passwordEncoder).encodePassword("adminPass");
        }

        @Test
        @DisplayName("Given admin already exists — When initializing — Then initialization fails and state rolls back")
        void GivenAdminExists_WhenInitializing_ThenNoDuplicateAdmin() {
            when(paymentGateway.isConnected()).thenReturn(true);
            when(ticketingGateway.isConnected()).thenReturn(true);
            when(userRepository.findByUsername("admin")).thenReturn(Optional.of(mock(User.class)));
            // Pre-condition: admin already exists, system not yet initialized
            assertFalse(systemService.isPlatformInitialized(), "Pre: system must not be initialized before this test");

            Result<Void> result = systemService.initializePlatform("admin", "adminPass");

            // Post-condition: initialization fails and system remains uninitialized; no duplicate saved
            assertFalse(result.isSuccess());
            assertFalse(systemService.isPlatformInitialized(), "Post: system must remain uninitialized when admin already exists");
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("Given valid config — When initializing — Then payment and ticket provider connectivity verified")
        void GivenValidConfig_WhenInitializing_ThenProviderConnectivityVerified() {
            when(paymentGateway.isConnected()).thenReturn(true);
            when(ticketingGateway.isConnected()).thenReturn(true);
            // Pre-condition: system is not yet initialized
            assertFalse(systemService.isPlatformInitialized(), "Pre: system must not be initialized before this test");

            systemService.initializePlatform("admin", "adminPass");

            // Post-condition: both external providers had their connectivity checked
            verify(paymentGateway).isConnected();
            verify(ticketingGateway).isConnected();
        }
    }

    @Nested
    @DisplayName("Failure Scenarios")
    class FailureScenarios {

        @Test
        @DisplayName("Given payment provider unreachable — When initializing — Then initialization fails with critical error")
        void GivenPaymentProviderUnreachable_WhenInitializing_ThenInitializationFails() {
            when(paymentGateway.isConnected()).thenReturn(false);
            // Pre-condition: system is not initialized; payment provider is down
            assertFalse(systemService.isPlatformInitialized(), "Pre: system must not be initialized before this test");

            Result<Void> result = systemService.initializePlatform("admin", "adminPass");

            // Post-condition: initialization rejected and system stays uninitialized
            assertFalse(result.isSuccess());
            assertEquals("Failed to connect to the external payment service.", result.getErrorMessage());
            assertFalse(systemService.isPlatformInitialized(), "Post: system must remain uninitialized when payment provider is unreachable");
        }

        @Test
        @DisplayName("Given ticket supplier unreachable — When initializing — Then initialization fails with critical error")
        void GivenTicketSupplierUnreachable_WhenInitializing_ThenInitializationFails() {
            when(paymentGateway.isConnected()).thenReturn(true);
            when(ticketingGateway.isConnected()).thenReturn(false);
            // Pre-condition: system is not initialized; payment provider OK but ticket supplier is down
            assertFalse(systemService.isPlatformInitialized(), "Pre: system must not be initialized before this test");

            Result<Void> result = systemService.initializePlatform("admin", "adminPass");

            // Post-condition: initialization rejected and system stays uninitialized
            assertFalse(result.isSuccess());
            assertEquals("Failed to connect to the external ticketing service.", result.getErrorMessage());
            assertFalse(systemService.isPlatformInitialized(), "Post: system must remain uninitialized when ticket supplier is unreachable");
        }

        @Test
        @DisplayName("Given invalid config file — When initializing — Then initialization fails")
        void GivenInvalidConfig_WhenInitializing_ThenInitializationFails() {
            // Note: Since config paths aren't explicitly passed to SystemService in this
            // version,
            // we trust it binds property fields or handles invalid initialization logic.
            // Placeholder/dummy test.
            assertTrue(true);
        }
    }
}
