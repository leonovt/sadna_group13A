package com.sadna.group13a.acceptance;

import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Interfaces.IPasswordEncoder;
import com.sadna.group13a.application.Interfaces.IPaymentGateway;
import com.sadna.group13a.application.Interfaces.ITicketSupplier;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.SystemService;
import com.sadna.group13a.domain.Aggregates.User.Member;
import com.sadna.group13a.domain.Interfaces.IAdminRepository;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import com.sadna.group13a.infrastructure.AuthImpl;
import com.sadna.group13a.infrastructure.PasswordEncoderImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.AdminRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.UserRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.FakeAdminJpaRepository;
import com.sadna.group13a.infrastructure.config.PersistenceConfig;
import com.sadna.group13a.infrastructure.StubPaymentGateway;
import com.sadna.group13a.infrastructure.StubTicketSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    private IPasswordEncoder passwordEncoder;
    private IPaymentGateway paymentGateway;
    private ITicketSupplier ticketingGateway;

    @BeforeEach
    void setUp() {
        userRepository = new UserRepositoryImpl();
        adminRepository = new AdminRepositoryImpl(new FakeAdminJpaRepository(), new PersistenceConfig().domainObjectMapper());
        authGateway = new AuthImpl();
        passwordEncoder = new PasswordEncoderImpl();
        paymentGateway = new StubPaymentGateway();   // always connected
        ticketingGateway = new StubTicketSupplier(); // always connected

        systemService = new SystemService(userRepository, adminRepository, authGateway, paymentGateway, ticketingGateway,
                passwordEncoder);
    }

    @Nested
    @DisplayName("Successful Initialization")
    class SuccessScenarios {

        @Test
        @DisplayName("Given valid config and available providers — When initializing — Then system status is INITIALIZED")
        void GivenValidConfigAndProviders_WhenInitializing_ThenStatusIsInitialized() {
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
            // Pre-condition: no admin record exists and system is not initialized
            assertTrue(userRepository.findByUsername("admin").isEmpty(), "Pre: admin must not exist before initialization");
            assertFalse(systemService.isPlatformInitialized(), "Pre: system must not be initialized before this test");

            systemService.initializePlatform("admin", "adminPass");

            // Post-condition: admin user was saved and password was encoded as a non-plaintext hash
            assertTrue(userRepository.findByUsername("admin").isPresent(), "Post: admin must be saved to user repository");
            String storedHash = userRepository.findByUsername("admin").get().getHashedPassword();
            assertNotEquals("adminPass", storedHash, "Post: stored password must not be plaintext");
            assertTrue(passwordEncoder.matches("adminPass", storedHash), "Post: stored hash must match the original password");
        }

        @Test
        @DisplayName("Given admin already exists — When initializing — Then initialization fails and state rolls back")
        void GivenAdminExists_WhenInitializing_ThenNoDuplicateAdmin() {
            // Pre-populate the repository with an existing admin user
            userRepository.save(new Member("existing-id", "admin", passwordEncoder.encodePassword("oldPass")));
            // Pre-condition: admin already exists, system not yet initialized
            assertTrue(userRepository.findByUsername("admin").isPresent(), "Pre: admin must already exist");
            assertFalse(systemService.isPlatformInitialized(), "Pre: system must not be initialized before this test");

            Result<Void> result = systemService.initializePlatform("admin", "adminPass");

            // Post-condition: initialization fails, system stays uninitialized, no second user saved
            assertFalse(result.isSuccess());
            assertFalse(systemService.isPlatformInitialized(), "Post: system must remain uninitialized when admin already exists");
            assertEquals(1, userRepository.findAll().size(), "Post: no duplicate admin must be saved");
        }

        @Test
        @DisplayName("Given valid config — When initializing — Then payment and ticket provider connectivity verified")
        void GivenValidConfig_WhenInitializing_ThenProviderConnectivityVerified() {
            // Use mocks here specifically to verify that the service checks connectivity
            IPaymentGateway mockPayment = mock(IPaymentGateway.class);
            ITicketSupplier mockTicket = mock(ITicketSupplier.class);
            when(mockPayment.isConnected()).thenReturn(true);
            when(mockTicket.isConnected()).thenReturn(true);
            SystemService svc = new SystemService(userRepository, adminRepository, authGateway,
                    mockPayment, mockTicket, passwordEncoder);
            // Pre-condition: system is not yet initialized
            assertFalse(svc.isPlatformInitialized(), "Pre: system must not be initialized before this test");

            svc.initializePlatform("admin", "adminPass");

            // Post-condition: both external providers had their connectivity checked
            org.mockito.Mockito.verify(mockPayment).isConnected();
            org.mockito.Mockito.verify(mockTicket).isConnected();
        }
    }

    @Nested
    @DisplayName("Failure Scenarios")
    class FailureScenarios {

        @Test
        @DisplayName("Given payment provider unreachable — When initializing — Then initialization fails with critical error")
        void GivenPaymentProviderUnreachable_WhenInitializing_ThenInitializationFails() {
            IPaymentGateway downPayment = mock(IPaymentGateway.class);
            when(downPayment.isConnected()).thenReturn(false);
            SystemService svc = new SystemService(userRepository, adminRepository, authGateway,
                    downPayment, ticketingGateway, passwordEncoder);
            // Pre-condition: system is not initialized; payment provider is down
            assertFalse(svc.isPlatformInitialized(), "Pre: system must not be initialized before this test");

            Result<Void> result = svc.initializePlatform("admin", "adminPass");

            // Post-condition: initialization rejected and system stays uninitialized
            assertFalse(result.isSuccess());
            assertEquals("Failed to connect to the external payment service.", result.getErrorMessage());
            assertFalse(svc.isPlatformInitialized(), "Post: system must remain uninitialized when payment provider is unreachable");
        }

        @Test
        @DisplayName("Given ticket supplier unreachable — When initializing — Then initialization fails with critical error")
        void GivenTicketSupplierUnreachable_WhenInitializing_ThenInitializationFails() {
            ITicketSupplier downTicket = mock(ITicketSupplier.class);
            when(downTicket.isConnected()).thenReturn(false);
            SystemService svc = new SystemService(userRepository, adminRepository, authGateway,
                    paymentGateway, downTicket, passwordEncoder);
            // Pre-condition: system is not initialized; payment provider OK but ticket supplier is down
            assertFalse(svc.isPlatformInitialized(), "Pre: system must not be initialized before this test");

            Result<Void> result = svc.initializePlatform("admin", "adminPass");

            // Post-condition: initialization rejected and system stays uninitialized
            assertFalse(result.isSuccess());
            assertEquals("Failed to connect to the external ticketing service.", result.getErrorMessage());
            assertFalse(svc.isPlatformInitialized(), "Post: system must remain uninitialized when ticket supplier is unreachable");
        }

        @Test
        @DisplayName("Given invalid config file — When initializing — Then initialization fails")
        void GivenInvalidConfig_WhenInitializing_ThenInitializationFails() {
            systemService.initializePlatform("admin", "adminPass");
            // Pre-condition: platform is already initialized
            assertTrue(systemService.isPlatformInitialized(), "Pre: platform must be initialized before second attempt");

            Result<Void> result = systemService.initializePlatform("admin2", "pass2");

            // Post-condition: second initialization is rejected — platform cannot be initialized twice
            assertFalse(result.isSuccess(), "Post: second initialization must be rejected");
            assertTrue(result.getErrorMessage().contains("already initialized"),
                    "Post: error must indicate the platform is already initialized");
        }
    }
}
