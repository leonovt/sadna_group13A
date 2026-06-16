package com.sadna.group13a.config;

import com.sadna.group13a.application.Interfaces.IPaymentGateway;
import com.sadna.group13a.application.Interfaces.ITicketSupplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves the application context boots fully offline under the dedicated "test" profile
 * (issue #231): external gateways resolve to in-memory stubs and the ticketing URL points
 * to a local stub — no real database or external endpoint is contacted.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Issue #231 — Context boots offline under the test profile")
class TestProfileContextLoadsTest {

    @Autowired
    private ITicketSupplier ticketSupplier;

    @Autowired
    private IPaymentGateway paymentGateway;

    @Value("${app.ticketing.url}")
    private String ticketingUrl;

    @Test
    @DisplayName("Gateways are offline stubs and external URL is a local stub")
    void contextLoadsWithOfflineStubs() {
        assertTrue(ticketSupplier.isConnected(), "stub ticket supplier should report connected offline");
        assertTrue(paymentGateway.isConnected(), "stub payment gateway should report connected offline");
        assertTrue(ticketingUrl.startsWith("http://localhost"),
                "test profile must point the ticketing URL at a local stub, was: " + ticketingUrl);
    }
}
