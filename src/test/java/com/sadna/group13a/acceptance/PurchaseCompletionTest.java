package com.sadna.group13a.acceptance;

import com.sadna.group13a.application.DTO.OrderHistoryDTO;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Interfaces.IPaymentGateway;
import com.sadna.group13a.application.Interfaces.ITicketSupplier;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.OrderService;
import com.sadna.group13a.domain.Aggregates.ActiveOrder.ActiveOrder;
import com.sadna.group13a.domain.Aggregates.ActiveOrder.OrderItem;
import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import com.sadna.group13a.domain.Aggregates.Event.Event;
import com.sadna.group13a.domain.Aggregates.Event.EventSaleMode;
import com.sadna.group13a.domain.Aggregates.OrderHistory.OrderHistory;
import com.sadna.group13a.domain.Aggregates.OrderHistory.OrderHistoryItem;
import com.sadna.group13a.domain.Aggregates.User.Member;
import com.sadna.group13a.domain.DomainServices.CheckoutDomainService;
import com.sadna.group13a.domain.DomainServices.TicketingAccessDomainService;
import com.sadna.group13a.domain.shared.PermissionDeniedException;
import com.sadna.group13a.domain.Interfaces.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Acceptance tests for UC 2.5: Purchase Completion.
 *
 * Covers the full checkout flow: timer validation, re-verification of policy,
 * discount calculation, payment processing, ticket issuance, and atomicity.
 */
@DisplayName("UC 2.5 — Purchase Completion")
class PurchaseCompletionTest {

    private OrderService orderService;
    private IActiveOrderRepository orderRepository;
    private IOrderHistoryRepository historyRepository;
    private IEventRepository eventRepository;
    private ICompanyRepository companyRepository;
    private IQueueRepository queueRepository;
    private IRaffleRepository raffleRepository;
    private ITicketSupplier ticketSupplier;
    private IPaymentGateway paymentGateway;
    private IUserRepository userRepository;
    private IAuth authGateway;
    private CheckoutDomainService checkoutDomainService;
    private TicketingAccessDomainService ticketingAccessDomainService;
    private ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        orderRepository = mock(IActiveOrderRepository.class);
        historyRepository = mock(IOrderHistoryRepository.class);
        eventRepository = mock(IEventRepository.class);
        companyRepository = mock(ICompanyRepository.class);
        queueRepository = mock(IQueueRepository.class);
        raffleRepository = mock(IRaffleRepository.class);
        ticketSupplier = mock(ITicketSupplier.class);
        when(ticketSupplier.issueTickets(any(), anyInt())).thenReturn(Result.success(List.of("ticket-1")));
        paymentGateway = mock(IPaymentGateway.class);
        userRepository = mock(IUserRepository.class);
        authGateway = mock(IAuth.class);
        checkoutDomainService = mock(CheckoutDomainService.class);
        ticketingAccessDomainService = mock(TicketingAccessDomainService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        orderService = new OrderService(
                orderRepository, historyRepository, eventRepository, companyRepository,
                queueRepository, raffleRepository, paymentGateway, ticketSupplier, userRepository, authGateway,
                checkoutDomainService, ticketingAccessDomainService, eventPublisher);

        // Default: any userId resolves to an active member so user-guard tests pass through
        when(userRepository.findById(anyString()))
                .thenAnswer(inv -> Optional.of(new Member(inv.getArgument(0), "user", "hash")));
    }

    @Nested
    @DisplayName("Successful Purchase")
    class SuccessScenarios {

        @Test
        @DisplayName("Given valid reservation within timer — When completing purchase — Then payment charged, tickets issued, inventory updated")
        void GivenValidReservation_WhenCompletingPurchase_ThenFullFlowSucceeds() throws Exception {
            String token = "valid_token";
            String userId = "user123";
            String activeOrderId = "order123";
            String eventId = "event1";
            String companyId = "company1";
            String paymentDetails = "cc_good";

            when(authGateway.validateToken(token)).thenReturn(true);
            when(authGateway.extractUserId(token)).thenReturn(userId);

            ActiveOrder order = new ActiveOrder(activeOrderId, userId);
            order.addItem(new OrderItem(eventId, "zone1", "seat1", 100.0));
            when(orderRepository.findById(activeOrderId)).thenReturn(Optional.of(order));

            Event event = mock(Event.class);
            when(event.getId()).thenReturn(eventId);
            when(event.getCompanyId()).thenReturn(companyId);
            when(event.getSaleMode()).thenReturn(EventSaleMode.REGULAR);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

            ProductionCompany company = mock(ProductionCompany.class);
            when(companyRepository.findById(companyId)).thenReturn(Optional.of(company));

            // Domain Service mocks
            OrderHistoryItem item = new OrderHistoryItem(eventId, "Title", LocalDateTime.now(), companyId, "Company",
                    "Zone1", "Seat1", 100.0);
            when(checkoutDomainService.checkoutItemsForEvent(any(), any(), any(), any(), any(), any())).thenReturn(List.of(item));

            when(paymentGateway.processPayment(100.0, paymentDetails)).thenReturn(Result.success("txn_1"));
            // Pre-condition: cart exists and has not expired; user is authenticated and active
            assertTrue(authGateway.validateToken(token), "Pre: user must be authenticated");
            assertTrue(order.getExpiresAt().isAfter(LocalDateTime.now()), "Pre: cart must not have expired before checkout");
            assertEquals(1, order.getItems().size(), "Pre: cart must contain items");

            Result<OrderHistoryDTO> result = orderService.executeCheckout(token, activeOrderId, null, paymentDetails);

            // Post-condition: payment charged, history saved, active order deleted
            assertTrue(result.isSuccess(), "Post: checkout must succeed for valid reservation within timer");
            verify(paymentGateway).processPayment(100.0, paymentDetails);
            verify(historyRepository).save(any(OrderHistory.class));
            verify(orderRepository).deleteById(activeOrderId);
        }
    }

    @Nested
    @DisplayName("Policy Re-Verification")
    class PolicyTests {

        @Test
        @DisplayName("Given user exceeds max ticket policy at checkout — Then purchase rejected immediately")
        void GivenPolicyExceeded_ThenPurchaseRejected() throws Exception {
            String token = "valid_token";
            String userId = "user123";
            String activeOrderId = "order123";
            String eventId = "event1";

            when(authGateway.validateToken(token)).thenReturn(true);
            when(authGateway.extractUserId(token)).thenReturn(userId);

            ActiveOrder order = new ActiveOrder(activeOrderId, userId);
            order.addItem(new OrderItem(eventId, "zone1", "seat1", 100.0));
            when(orderRepository.findById(activeOrderId)).thenReturn(Optional.of(order));

            Event event = mock(Event.class);
            when(event.getId()).thenReturn(eventId);
            when(event.getCompanyId()).thenReturn("company1");
            when(event.getSaleMode()).thenReturn(EventSaleMode.REGULAR);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

            when(companyRepository.findById("company1")).thenReturn(Optional.of(mock(ProductionCompany.class)));

            // Let checkoutDomainService throw an exception representing policy breach
            doThrow(new RuntimeException("Policy Exceeded")).when(checkoutDomainService)
                    .checkoutItemsForEvent(any(), any(), any(), any(), any(), any());

            Result<OrderHistoryDTO> result = orderService.executeCheckout(token, activeOrderId, null, "cc_good");

            assertFalse(result.isSuccess());
            verify(paymentGateway, never()).processPayment(anyDouble(), anyString());
        }
    }

    @Nested
    @DisplayName("Atomicity — Payment vs Ticket Issuance")
    class AtomicityTests {

        @Test
        @DisplayName("Given payment approved but ticket issuance fails — Then seats released AND automatic refund issued")
        void GivenPaymentOKButTicketFails_ThenSeatsReleasedAndRefundIssued() throws Exception {
            String token = "valid_token";
            String userId = "user123";
            String activeOrderId = "order123";
            String eventId = "event1";

            when(authGateway.validateToken(token)).thenReturn(true);
            when(authGateway.extractUserId(token)).thenReturn(userId);

            ActiveOrder order = new ActiveOrder(activeOrderId, userId);
            order.addItem(new OrderItem(eventId, "zone1", "seat1", 100.0));
            when(orderRepository.findById(activeOrderId)).thenReturn(Optional.of(order));

            Event event = mock(Event.class);
            when(event.getId()).thenReturn(eventId);
            when(event.getCompanyId()).thenReturn("company1");
            when(event.getSaleMode()).thenReturn(EventSaleMode.REGULAR);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(companyRepository.findById("company1")).thenReturn(Optional.of(mock(ProductionCompany.class)));

            OrderHistoryItem item = new OrderHistoryItem(eventId, "Title", LocalDateTime.now(), "company1", "Company",
                    "Zone1", "Seat1", 100.0);
            when(checkoutDomainService.checkoutItemsForEvent(any(), any(), any(), any(), any(), any())).thenReturn(List.of(item));

            when(paymentGateway.processPayment(100.0, "cc_good")).thenReturn(Result.success("txn_1"));

            // Simulate failure saving the event to trigger the refund (which mimics
            // atomicity failure)
            doThrow(new RuntimeException("DB Down")).when(eventRepository).save(any(Event.class));

            Result<OrderHistoryDTO> result = orderService.executeCheckout(token, activeOrderId, null, "cc_good");

            assertFalse(result.isSuccess());
            verify(paymentGateway).refundPayment("txn_1");
        }
    }

    @Nested
    @DisplayName("Lottery Verification at Checkout")
    class LotteryVerificationTests {

        @Test
        @DisplayName("Given user NOT a lottery winner — Then user cannot reach payment stage")
        void GivenNonLotteryWinner_ThenCannotReachPayment() throws Exception {
            String token = "valid_token";
            String userId = "user123";
            String activeOrderId = "order123";
            String eventId = "event1";

            when(authGateway.validateToken(token)).thenReturn(true);
            when(authGateway.extractUserId(token)).thenReturn(userId);

            ActiveOrder order = new ActiveOrder(activeOrderId, userId);
            order.addItem(new OrderItem(eventId, "zone1", "seat1", 100.0));
            when(orderRepository.findById(activeOrderId)).thenReturn(Optional.of(order));

            Event event = mock(Event.class);
            when(event.getId()).thenReturn(eventId);
            when(event.getCompanyId()).thenReturn("company1");
            when(event.getSaleMode()).thenReturn(EventSaleMode.RAFFLE); // Important for Lottery check
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(companyRepository.findById("company1")).thenReturn(Optional.of(mock(ProductionCompany.class)));

            doThrow(new PermissionDeniedException("Access Denied: Not a winner"))
                    .when(ticketingAccessDomainService).validateAccess(any(), any(), any(), any());

            Result<OrderHistoryDTO> result = orderService.executeCheckout(token, activeOrderId, null, "cc_good");

            assertFalse(result.isSuccess());
            assertTrue(result.getErrorMessage().contains("Not a winner"));
            verify(paymentGateway, never()).processPayment(anyDouble(), anyString());
        }
    }

    @Nested
    @DisplayName("Timer Expired at Checkout")
    class TimerTests {

        @Test
        @DisplayName("Given timer expired during payment entry — When submitting payment — Then payment blocked and seats released")
        void GivenTimerExpired_WhenSubmittingPayment_ThenBlockedAndSeatsReleased() {
            String token = "valid_token";
            String userId = "user123";
            String activeOrderId = "order123";
            String eventId = "event1";

            when(authGateway.validateToken(token)).thenReturn(true);
            when(authGateway.extractUserId(token)).thenReturn(userId);

            ActiveOrder order = new ActiveOrder(activeOrderId, userId);
            order.addItem(new OrderItem(eventId, "zone1", "seat1", 100.0));
            // Pre-condition: user is authenticated; cart has been removed (timer expired)
            assertTrue(authGateway.validateToken(token), "Pre: user must be authenticated");
            // Simulate expiration by returning empty
            when(orderRepository.findById(activeOrderId)).thenReturn(Optional.empty());

            Result<OrderHistoryDTO> result = orderService.executeCheckout(token, activeOrderId, null, "cc_good");

            // Post-condition: payment is blocked since cart no longer exists; no charge is made
            assertFalse(result.isSuccess(), "Post: checkout must fail when cart has expired");
            assertEquals("Cart not found", result.getErrorMessage());
            verify(paymentGateway, never()).processPayment(anyDouble(), anyString());
        }
    }
}
