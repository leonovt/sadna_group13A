package com.sadna.group13a;

import com.sadna.group13a.application.Interfaces.IPaymentGateway;
import com.sadna.group13a.application.Interfaces.ITicketSupplier;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.CompanyService;
import com.sadna.group13a.application.Services.SystemService;
import com.sadna.group13a.application.Services.UserService;
import com.sadna.group13a.infrastructure.StubPaymentGateway;
import com.sadna.group13a.infrastructure.StubTicketSupplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Full-context integration test: boots the application under the {@code test} profile
 * and verifies the startup sequence completes without error.
 *
 * Catches the most common misconfiguration mistakes:
 * - Missing or bad {@code @Entity} / JPA column mapping (schema generation fails)
 * - Ambiguous bean / missing {@code @Profile} binding (context fails to start)
 * - {@link com.sadna.group13a.infrastructure.PlatformBootstrap} failure (gateway connectivity, admin creation)
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Application startup — Full context boots offline under test profile")
class ApplicationStartupTest {

    @Autowired private IPaymentGateway paymentGateway;
    @Autowired private ITicketSupplier ticketSupplier;
    @Autowired private SystemService systemService;
    @Autowired private UserService userService;
    @Autowired private CompanyService companyService;

    @Test
    @DisplayName("Context loads and stub gateways are injected, not real HTTP clients")
    void contextLoads() {
        assertInstanceOf(StubPaymentGateway.class, paymentGateway,
                "test profile must inject StubPaymentGateway — real WSEP client must not be active");
        assertInstanceOf(StubTicketSupplier.class, ticketSupplier,
                "test profile must inject StubTicketSupplier — real external client must not be active");
    }

    @Test
    @DisplayName("PlatformBootstrap completed — platform reports initialized after startup")
    void platformBootstrapCompleted() {
        assertTrue(systemService.isPlatformInitialized(),
                "PlatformBootstrap must have initialized the platform during application startup");
    }

    @Test
    @DisplayName("Smoke: register → login → open production company exercises the full service + repository stack")
    void registerLoginAndOpenCompany() {
        // Use a username unlikely to collide with admin or other test users.
        String username = "smoke-test-user";
        String password  = "smoke-pass-999";

        Result<?> registerResult = userService.register(username, password);
        assertTrue(registerResult.isSuccess(),
                "Registration must succeed; got: " + registerResult.getErrorMessage());

        Result<String> loginResult = userService.login(username, password);
        assertTrue(loginResult.isSuccess(),
                "Login must succeed after registration; got: " + loginResult.getErrorMessage());

        String token = loginResult.getOrThrow();
        Result<?> companyResult = companyService.createCompany(token, "Smoke Test Co", "Integration test");
        assertTrue(companyResult.isSuccess(),
                "Company creation must succeed for a logged-in member; got: " + companyResult.getErrorMessage());
    }
}
