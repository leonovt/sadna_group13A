package com.sadna.group13a.application.Services;

import com.sadna.group13a.application.DTO.OrderHistoryDTO;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Interfaces.IPaymentGateway;
import com.sadna.group13a.application.Interfaces.ITicketSupplier;
import com.sadna.group13a.application.Interfaces.TicketIssueRequest;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.domain.Aggregates.ActiveOrder.ActiveOrder;
import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import com.sadna.group13a.domain.Aggregates.Event.Event;
import com.sadna.group13a.domain.Aggregates.Event.Seat;
import com.sadna.group13a.domain.Aggregates.Event.SeatedZone;
import com.sadna.group13a.domain.Aggregates.Event.SeatStatus;
import com.sadna.group13a.domain.Aggregates.Event.VenueMap;
import com.sadna.group13a.domain.Aggregates.User.Member;
import com.sadna.group13a.domain.DomainServices.CartDomainService;
import com.sadna.group13a.domain.DomainServices.CheckoutDomainService;
import com.sadna.group13a.domain.DomainServices.TicketingAccessDomainService;
import com.sadna.group13a.infrastructure.RepositoryImpl.ActiveOrderRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.CompanyRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.EventRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.OrderHistoryRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.QueueRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.RaffleRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.UserRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.FakeActiveOrderJpaRepository;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.FakeCompanyJpaRepository;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.FakeEventJpaRepository;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.FakeOrderHistoryJpaRepository;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.FakeRaffleJpaRepository;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.FakeUserJpaRepository;
import com.sadna.group13a.infrastructure.config.PersistenceConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for issue #244 — Payment system failure scenarios.
 *
 * <p>Every test in this class uses:
 * <ul>
 *   <li>Real in-memory repositories so that seat state and history records can be
 *       verified through the repository after the checkout call completes.</li>
 *   <li>Real {@link CheckoutDomainService} so that the full domain-checkout logic
 *       (seat HELD → SOLD → rollback to AVAILABLE) executes exactly as it would in
 *       production.</li>
 *   <li>{@code Mockito.mock(IPaymentGateway.class)} for the payment gateway so that
 *       each test can configure the exact failure mode (declined result, timeout result,
 *       unchecked exception) without touching any HTTP endpoint.</li>
 * </ul>
 *
 * <p>No Spring context is loaded — the service is wired by hand for fast, focused tests.
 */
@DisplayName("[V3] #244 — Payment system failure scenarios")
class PaymentFailureRobustnessTest {

    // ── Stable identifiers ─────────────────────────────────────────────────────

    private static final String USER_ID     = "user-pf-001";
    private static final String COMPANY_ID  = "company-pf-001";
    private static final String EVENT_ID    = "event-pf-001";
    private static final String ZONE_ID     = "zone-pf-001";
    private static final String SEAT_ID     = "seat-pf-001";
    private static final String SEAT_LABEL  = "B-7";
    private static final double SEAT_PRICE  = 120.0;
    private static final String VALID_TOKEN = "payment-failure-test-token";

    // ── Real in-memory repositories ────────────────────────────────────────────

    private ActiveOrderRepositoryImpl  orderRepo;
    private OrderHistoryRepositoryImpl historyRepo;
    private EventRepositoryImpl        eventRepo;
    private CompanyRepositoryImpl      companyRepo;
    private QueueRepositoryImpl        queueRepo;
    private RaffleRepositoryImpl       raffleRepo;
    private UserRepositoryImpl         userRepo;

    // ── Mockito mock for the payment gateway ───────────────────────────────────

    private IPaymentGateway paymentGateway;
    private SystemLogService systemLogService;

    // ── Lightweight test doubles for non-payment external ports ───────────────

    private StubAuth          auth;
    private StubTicketSupplier ticketSupplier;

    // ── Real domain services (stateless) ──────────────────────────────────────

    private CheckoutDomainService        checkoutDomainService;
    private TicketingAccessDomainService ticketingAccessDomainService;
    private CartDomainService            cartDomainService;

    // ── System Under Test ─────────────────────────────────────────────────────

    private OrderService orderService;

    // ─────────────────────────────────────────────────────────────────────────
    // Setup
    // ─────────────────────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        PersistenceConfig cfg = new PersistenceConfig();
        orderRepo   = new ActiveOrderRepositoryImpl(new FakeActiveOrderJpaRepository(),  cfg.domainObjectMapper());
        historyRepo = new OrderHistoryRepositoryImpl(new FakeOrderHistoryJpaRepository(), cfg.domainObjectMapper());
        eventRepo   = new EventRepositoryImpl(new FakeEventJpaRepository(),               cfg.domainObjectMapper());
        companyRepo = new CompanyRepositoryImpl(new FakeCompanyJpaRepository(),           cfg.domainObjectMapper());
        queueRepo   = new QueueRepositoryImpl();
        raffleRepo  = new RaffleRepositoryImpl(new FakeRaffleJpaRepository(),             cfg.domainObjectMapper());
        userRepo    = new UserRepositoryImpl(new FakeUserJpaRepository(),                 cfg.domainObjectMapper());

        checkoutDomainService        = new CheckoutDomainService();
        ticketingAccessDomainService = new TicketingAccessDomainService();
        cartDomainService            = new CartDomainService();

        // IPaymentGateway is the only Mockito mock — all other collaborators are real.
        paymentGateway   = mock(IPaymentGateway.class);
        systemLogService = mock(SystemLogService.class);

        auth           = new StubAuth(USER_ID, VALID_TOKEN);
        ticketSupplier = new StubTicketSupplier();

        // No-op publisher: payment failure tests do not exercise notification paths.
        ApplicationEventPublisher noOpPublisher = event -> {};

        orderService = new OrderService(
                orderRepo,                    // 1. IActiveOrderRepository
                historyRepo,                  // 2. IOrderHistoryRepository
                eventRepo,                    // 3. IEventRepository
                companyRepo,                  // 4. ICompanyRepository
                queueRepo,                    // 5. IQueueRepository
                raffleRepo,                   // 6. IRaffleRepository
                paymentGateway,               // 7. IPaymentGateway  ← mocked
                ticketSupplier,               // 8. ITicketSupplier
                userRepo,                     // 9. IUserRepository
                auth,                         // 10. IAuth
                checkoutDomainService,        // 11. CheckoutDomainService
                ticketingAccessDomainService, // 12. TicketingAccessDomainService
                noOpPublisher,                // 13. ApplicationEventPublisher
                cartDomainService,            // 14. CartDomainService
                null,                         // 15. QueueService (not exercised here)
                systemLogService              // 16. SystemLogService
        );

        seedEventCompanyAndUser();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Seed helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Seeds a published event with one available seat and its owning company and buyer. */
    private void seedEventCompanyAndUser() {
        companyRepo.save(new ProductionCompany(
                COMPANY_ID, "Failure-Test Events Ltd.", "Unit-test company", USER_ID));

        Seat       seat    = new Seat(SEAT_ID, SEAT_LABEL);
        SeatedZone zone    = new SeatedZone(ZONE_ID, "Main Floor", SEAT_PRICE, List.of(seat));
        VenueMap   vm      = new VenueMap("vm-pf-001", "Test Arena", List.of(zone));

        Event event = new Event(
                EVENT_ID, "Payment-Failure Test Concert", "desc",
                COMPANY_ID, LocalDateTime.now().plusDays(14), "Test");
        event.setVenueMap(vm);
        event.publish();
        eventRepo.save(event);

        userRepo.save(new Member(USER_ID, "paymentFailureUser", "hash-irrelevant"));
    }

    /**
     * Adds the seeded seat to the user's cart via the service.
     * Fails fast if setup itself fails so test bodies stay clean.
     *
     * @return the active-order ID of the created cart
     */
    private String placeItemInCart() {
        Result<String> addResult = orderService.addItemToCart(VALID_TOKEN, EVENT_ID, ZONE_ID, SEAT_ID);
        assertTrue(addResult.isSuccess(),
                "Test setup: addItemToCart must succeed; actual error: "
                        + addResult.getErrorMessage());
        return addResult.getData().orElseThrow();
    }

    /** Reads the seat from the (real) event repository. */
    private Seat readSeatFromRepo() {
        Event persistedEvent = eventRepo.findById(EVENT_ID).orElseThrow();
        SeatedZone persistedZone = (SeatedZone) persistedEvent.getZoneById(ZONE_ID);
        return persistedZone.findSeatById(SEAT_ID).orElseThrow();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test cases
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Payment returns a failure result (no exception)")
    class PaymentReturnsFailureResult {

        @Test
        @DisplayName("checkout_paymentReturnsMinusOne_throwsPaymentFailedAndRollsBack: "
                + "gateway returns decline (maps to -1/rejected code) → checkout fails, seats rolled back")
        void checkout_paymentReturnsMinusOne_throwsPaymentFailedAndRollsBack() {
            // The external system returns a failure code (analogous to HTTP -1 / declined).
            when(paymentGateway.processPayment(anyDouble(), anyString()))
                    .thenReturn(Result.failure("declined (-1)"));

            String cartId = placeItemInCart();

            // Seat is HELD at this point — verify pre-condition.
            assertEquals(SeatStatus.HELD, readSeatFromRepo().getEffectiveStatus(),
                    "Pre: seat must be HELD after addItemToCart");

            Result<OrderHistoryDTO> result =
                    orderService.executeCheckout(VALID_TOKEN, cartId, null, "declined-card");

            // Checkout must fail.
            assertFalse(result.isSuccess(),
                    "Checkout must fail when the payment gateway returns a declined result");
            assertTrue(result.getErrorMessage().contains("Payment declined"),
                    "Error message must indicate the payment was declined");

            // Rollback must have been executed — seat is available again.
            assertEquals(SeatStatus.AVAILABLE, readSeatFromRepo().getEffectiveStatus(),
                    "Seat must revert to AVAILABLE after payment-decline rollback");
        }

        @Test
        @DisplayName("checkout_paymentTimesOut_throwsPaymentFailedAndRollsBack: "
                + "gateway returns timeout failure → checkout fails, seats rolled back")
        void checkout_paymentTimesOut_throwsPaymentFailedAndRollsBack() {
            // A timeout at the HTTP layer is surfaced as Result.failure with a timeout message.
            when(paymentGateway.processPayment(anyDouble(), anyString()))
                    .thenReturn(Result.failure("Request timeout after 30s — no response from payment provider"));

            String cartId = placeItemInCart();

            Result<OrderHistoryDTO> result =
                    orderService.executeCheckout(VALID_TOKEN, cartId, null, "card-timeout");

            assertFalse(result.isSuccess(),
                    "Checkout must fail when the payment gateway reports a timeout");
            assertTrue(result.getErrorMessage().contains("Payment declined"),
                    "Error message must wrap the gateway's timeout reason");

            assertEquals(SeatStatus.AVAILABLE, readSeatFromRepo().getEffectiveStatus(),
                    "Seat must revert to AVAILABLE after a timeout-induced rollback");
        }

        @Test
        @DisplayName("checkout_paymentFails_noOrderHistoryCreated: "
                + "no OrderHistory record must exist after a payment failure")
        void checkout_paymentFails_noOrderHistoryCreated() {
            when(paymentGateway.processPayment(anyDouble(), anyString()))
                    .thenReturn(Result.failure("Card declined by issuing bank"));

            String cartId = placeItemInCart();
            orderService.executeCheckout(VALID_TOKEN, cartId, null, "bad-card");

            assertTrue(historyRepo.findByUserId(USER_ID).isEmpty(),
                    "No OrderHistory must be persisted when payment fails — "
                            + "history is only created after a successful charge");
        }

        @Test
        @DisplayName("checkout_paymentFails_activeOrderRemainsOpen: "
                + "the user's cart must survive a payment failure so they can retry")
        void checkout_paymentFails_activeOrderRemainsOpen() {
            when(paymentGateway.processPayment(anyDouble(), anyString()))
                    .thenReturn(Result.failure("Insufficient funds"));

            String cartId = placeItemInCart();
            orderService.executeCheckout(VALID_TOKEN, cartId, null, "bad-card");

            // Cart must not have been deleted.
            assertTrue(orderRepo.findById(cartId).isPresent(),
                    "ActiveOrder must remain in the repository after payment failure");
            assertFalse(orderRepo.findActiveByUserId(USER_ID).isEmpty(),
                    "User must still have an active cart so they can retry checkout");

            // The cart's items must also still be intact.
            ActiveOrder survivingCart = orderRepo.findById(cartId).orElseThrow();
            assertEquals(1, survivingCart.getItems().size(),
                    "Items in the surviving cart must be unchanged after payment failure");
        }

        @Test
        @DisplayName("Payment gateway must be invoked exactly once per checkout attempt")
        void checkout_paymentFails_paymentGatewayCalledExactlyOnce() {
            when(paymentGateway.processPayment(anyDouble(), anyString()))
                    .thenReturn(Result.failure("Declined"));

            String cartId = placeItemInCart();
            orderService.executeCheckout(VALID_TOKEN, cartId, null, "bad-card");

            // No retry logic must exist at this layer — one call, one failure.
            verify(paymentGateway, times(1)).processPayment(SEAT_PRICE, "bad-card");
        }

        @Test
        @DisplayName("Refund must NOT be called when payment itself failed (nothing was charged)")
        void checkout_paymentDeclined_refundNotCalled() {
            when(paymentGateway.processPayment(anyDouble(), anyString()))
                    .thenReturn(Result.failure("Declined"));

            String cartId = placeItemInCart();
            orderService.executeCheckout(VALID_TOKEN, cartId, null, "bad-card");

            // A refund on a declined charge would be a double-error — must never happen.
            verify(paymentGateway, never()).refundPayment(anyString());
            verify(paymentGateway, never()).refundPartial(anyString(), anyDouble());
        }
    }

    @Nested
    @DisplayName("Payment gateway throws a RuntimeException (connection/timeout error)")
    class PaymentGatewayThrowsException {

        /**
         * Verifies that when the payment gateway throws an unchecked exception
         * (e.g., a TCP connection reset or a read timeout), the checkout call does
         * <em>not</em> propagate that exception to the caller and the seat is not
         * left in a permanently SOLD state.
         *
         * <p><strong>Depends on issue #242</strong> — until {@code OrderService} wraps
         * {@code processPayment} in a try-catch and calls {@code rollbackSoldSeats} on
         * exception, this test will fail because the RuntimeException propagates
         * uncaught from {@code executeCheckout}.
         */
        @Test
        @DisplayName("checkout_paymentThrowsException_seatsStillAvailable: "
                + "RuntimeException from gateway must not leave seats permanently SOLD")
        void checkout_paymentThrowsException_seatsStillAvailable() {
            when(paymentGateway.processPayment(anyDouble(), anyString()))
                    .thenThrow(new RuntimeException("Connection reset by peer"));

            String cartId = placeItemInCart();

            // executeCheckout must catch the exception and return Result.failure (see #242).
            // Until #242 is implemented this assertion fails because the RuntimeException
            // propagates out of executeCheckout instead of being wrapped.
            Result<OrderHistoryDTO> result = assertDoesNotThrow(
                    () -> orderService.executeCheckout(VALID_TOKEN, cartId, null, "card"),
                    "executeCheckout must catch RuntimeException from the payment gateway "
                            + "and return Result.failure instead of throwing (requires #242)");

            assertFalse(result.isSuccess(),
                    "Checkout must return failure when the gateway throws an exception");

            // The seat must not be permanently SOLD — the user should be able to retry.
            SeatStatus seatStatus = readSeatFromRepo().getEffectiveStatus();
            assertNotEquals(SeatStatus.SOLD, seatStatus,
                    "Seat must not remain SOLD after payment threw an exception — "
                            + "it must be rolled back to AVAILABLE (requires #242)");

            // No history record must have been created.
            assertTrue(historyRepo.findByUserId(USER_ID).isEmpty(),
                    "No OrderHistory must be persisted when payment threw an exception");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test infrastructure
    // ─────────────────────────────────────────────────────────────────────────

    /** Validates a single hard-coded token/userId pair without touching JWT. */
    static class StubAuth implements IAuth {

        private final String userId;
        private final String validToken;

        StubAuth(String userId, String validToken) {
            this.userId     = userId;
            this.validToken = validToken;
        }

        @Override public String generateToken(String uid)   { return validToken; }
        @Override public boolean validateToken(String token) { return validToken.equals(token); }
        @Override public String extractUserId(String token)  { return userId; }
    }

    /** Always succeeds at issuing tickets; never touches the external HTTP endpoint. */
    static class StubTicketSupplier implements ITicketSupplier {

        @Override public boolean isConnected() { return true; }

        @Override
        public Result<List<String>> issueTickets(String customerId, List<TicketIssueRequest> requests) {
            List<String> codes = new ArrayList<>();
            for (int i = 0; i < requests.size(); i++) {
                codes.add("TICKET-STUB-" + UUID.randomUUID());
            }
            return Result.success(codes);
        }

        @Override
        public Result<Void> cancelTickets(List<String> ticketCodes) {
            return Result.success();
        }
    }
}
