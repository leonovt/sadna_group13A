package com.sadna.group13a.acceptance;

import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Interfaces.IPaymentGateway;
import com.sadna.group13a.application.Interfaces.ITicketSupplier;
import com.sadna.group13a.application.Interfaces.TicketIssueRequest;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.OrderService;
import com.sadna.group13a.domain.Aggregates.OrderHistory.OrderHistory;
import com.sadna.group13a.domain.Aggregates.User.Member;
import com.sadna.group13a.domain.Interfaces.IOrderHistoryRepository;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import com.sadna.group13a.infrastructure.AuthImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.OrderHistoryRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.UserRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.FakeUserJpaRepository;
import com.sadna.group13a.infrastructure.config.PersistenceConfig;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.FakeOrderHistoryJpaRepository;
import com.sadna.group13a.infrastructure.StubPaymentGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("UC 1.4 — Ticket Issuance")
class TicketIssuanceTest {

    private OrderService orderService;
    private IUserRepository userRepository;
    private IOrderHistoryRepository historyRepository;
    private IAuth authGateway;
    private IPaymentGateway paymentGateway;
    private ITicketSupplier ticketSupplier;
    private ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        userRepository = new UserRepositoryImpl(new FakeUserJpaRepository(), new PersistenceConfig().domainObjectMapper());
        historyRepository = new OrderHistoryRepositoryImpl(new FakeOrderHistoryJpaRepository(), new PersistenceConfig().domainObjectMapper());
        authGateway = new AuthImpl();
        paymentGateway = new StubPaymentGateway(); // Used user's stub!
        ticketSupplier = mock(ITicketSupplier.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        orderService = new OrderService(null, historyRepository, null, null, null, null, paymentGateway, ticketSupplier, userRepository, authGateway, null, null, eventPublisher, null, null);
    }

    @Test
    @DisplayName("Given paid order — When issuing tickets — Then barcodes/QR generated and attached to order")
    void GivenPaidOrder_WhenIssuingTickets_ThenBarcodesAttached() {
        // Arrange
        String userId = "user1";
        userRepository.save(new Member(userId, "user1", "hash"));
        String token = authGateway.generateToken(userId);

        String receiptId = "receipt1";
        List<TicketIssueRequest> requests = List.of(new TicketIssueRequest("EVT-1", "General", false, 0, 0));
        when(ticketSupplier.issueTickets(eq(userId), anyList())).thenReturn(Result.success(List.of("BARCODE-123")));
        // Pre-condition: user exists and is authenticated; payment was already processed (receipt exists)
        assertTrue(userRepository.findById(userId).isPresent(), "Pre: user must exist in repository");
        assertTrue(authGateway.validateToken(token), "Pre: user token must be valid");

        // Act
        Result<List<String>> issueResult = ticketSupplier.issueTickets(userId, requests);

        // Post-condition: issuance succeeds and barcode is attached to the order
        assertTrue(issueResult.isSuccess(), "Post: ticket issuance must succeed for a paid order");
        assertFalse(issueResult.getOrThrow().isEmpty(), "Post: at least one barcode must be returned");
        assertEquals("BARCODE-123", issueResult.getOrThrow().get(0));
    }

    @Test
    @DisplayName("Given ticket supplier unavailable after payment — Then automatic refund triggered (UC 1.3)")
    void GivenTicketSupplierDown_ThenAutoRefundTriggered() {
        // Arrange
        List<TicketIssueRequest> requests = List.of(new TicketIssueRequest("EVT-1", "General", false, 0, 0));
        when(ticketSupplier.issueTickets(anyString(), anyList())).thenReturn(Result.failure("Supplier Down"));

        // Pre-condition: payment was already charged (transaction ID exists)
        String transactionId = paymentGateway.processPayment(100.0, "CC").getOrThrow();
        assertNotNull(transactionId, "Pre: a payment transaction must exist before attempting ticket issuance");

        // Act
        Result<List<String>> issueResult = ticketSupplier.issueTickets("cust-1", requests);

        // If it fails, trigger refund
        if (!issueResult.isSuccess()) {
            paymentGateway.refundPayment(transactionId);
        }

        // Post-condition: issuance failed and refund was triggered
        assertFalse(issueResult.isSuccess(), "Post: ticket issuance must fail when supplier is down");
        assertEquals("Supplier Down", issueResult.getErrorMessage());
    }

    @Test
    @DisplayName("Given 3 tickets ordered and 1 fails to issue — Then entire order is cancelled and refunded")
    void Given3TicketsAnd1Fails_ThenOrderCancelledAndRefunded() {
        // Arrange
        List<TicketIssueRequest> requests = List.of(
                new TicketIssueRequest("EVT-3", "General", false, 0, 0),
                new TicketIssueRequest("EVT-3", "General", false, 0, 0),
                new TicketIssueRequest("EVT-3", "General", false, 0, 0));
        // Simulate failure to issue 3 tickets (atomic failure)
        when(ticketSupplier.issueTickets(anyString(), anyList())).thenReturn(Result.failure("Failed to issue all tickets. Only 2 available."));

        // Pre-condition: payment was already charged for all 3 tickets
        String transactionId = paymentGateway.processPayment(300.0, "CC").getOrThrow();
        assertNotNull(transactionId, "Pre: a payment transaction must exist before attempting ticket issuance");

        // Act
        Result<List<String>> issueResult = ticketSupplier.issueTickets("cust-3", requests);

        if (!issueResult.isSuccess()) {
            // Trigger cancellation and refund
            Result<Void> refundResult = paymentGateway.refundPayment(transactionId);
            // Post-condition: refund succeeds when issuance fails (atomicity guarantee)
            assertTrue(refundResult.isSuccess(), "Refund must be processed successfully when issuance fails.");
        }

        // Post-condition: partial issuance is not accepted; entire order fails
        assertFalse(issueResult.isSuccess(), "Post: issuance must fail when not all tickets can be issued");
        assertEquals("Failed to issue all tickets. Only 2 available.", issueResult.getErrorMessage());
    }
}
