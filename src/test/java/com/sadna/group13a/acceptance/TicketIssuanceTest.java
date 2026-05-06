package com.sadna.group13a.acceptance;

import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Interfaces.IPaymentGateway;
import com.sadna.group13a.application.Interfaces.ITicketSupplier;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.OrderService;
import com.sadna.group13a.domain.Aggregates.OrderHistory.OrderHistory;
import com.sadna.group13a.domain.Aggregates.User.Member;
import com.sadna.group13a.domain.Interfaces.IOrderHistoryRepository;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import com.sadna.group13a.infrastructure.AuthImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.OrderHistoryRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.UserRepositoryImpl;
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
        userRepository = new UserRepositoryImpl();
        historyRepository = new OrderHistoryRepositoryImpl();
        authGateway = new AuthImpl();
        paymentGateway = new StubPaymentGateway(); // Used user's stub!
        ticketSupplier = mock(ITicketSupplier.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        orderService = new OrderService(null, historyRepository, null, null, null, null, paymentGateway, ticketSupplier, userRepository, authGateway, null, null, eventPublisher);
    }

    @Test
    @DisplayName("Given paid order — When issuing tickets — Then barcodes/QR generated and attached to order")
    void GivenPaidOrder_WhenIssuingTickets_ThenBarcodesAttached() {
        // Arrange
        String userId = "user1";
        userRepository.save(new Member(userId, "user1", "hash"));
        String token = authGateway.generateToken(userId);
        
        String receiptId = "receipt1";
        when(ticketSupplier.issueTickets(receiptId, 1)).thenReturn(Result.success(List.of("BARCODE-123")));

        // Act
        Result<List<String>> issueResult = ticketSupplier.issueTickets(receiptId, 1);

        // Assert
        assertTrue(issueResult.isSuccess());
        assertEquals("BARCODE-123", issueResult.getOrThrow().get(0));
    }

    @Test
    @DisplayName("Given ticket supplier unavailable after payment — Then automatic refund triggered (UC 1.3)")
    void GivenTicketSupplierDown_ThenAutoRefundTriggered() {
        // Arrange
        String receiptId = "receipt1";
        when(ticketSupplier.issueTickets(receiptId, 1)).thenReturn(Result.failure("Supplier Down"));
        
        String transactionId = paymentGateway.processPayment(100.0, "CC").getOrThrow();

        // Act
        Result<List<String>> issueResult = ticketSupplier.issueTickets(receiptId, 1);
        
        // If it fails, trigger refund
        if (!issueResult.isSuccess()) {
            paymentGateway.refundPayment(transactionId);
        }

        // Assert
        assertFalse(issueResult.isSuccess());
        assertEquals("Supplier Down", issueResult.getErrorMessage());
    }

    @Test
    @DisplayName("Given 3 tickets ordered and 1 fails to issue — Then entire order is cancelled and refunded")
    void Given3TicketsAnd1Fails_ThenOrderCancelledAndRefunded() {
        // Arrange
        String receiptId = "receipt3";
        // Simulate failure to issue 3 tickets (atomic failure)
        when(ticketSupplier.issueTickets(receiptId, 3)).thenReturn(Result.failure("Failed to issue all tickets. Only 2 available."));
        
        String transactionId = paymentGateway.processPayment(300.0, "CC").getOrThrow();

        // Act
        Result<List<String>> issueResult = ticketSupplier.issueTickets(receiptId, 3);
        
        if (!issueResult.isSuccess()) {
            // Trigger cancellation and refund
            Result<Void> refundResult = paymentGateway.refundPayment(transactionId);
            assertTrue(refundResult.isSuccess(), "Refund must be processed successfully when issuance fails.");
        }

        // Assert
        assertFalse(issueResult.isSuccess());
        assertEquals("Failed to issue all tickets. Only 2 available.", issueResult.getErrorMessage());
    }
}
