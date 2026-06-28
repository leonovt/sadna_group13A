package com.sadna.group13a.application.Services;

import com.sadna.group13a.application.DTO.OrderHistoryDTO;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Interfaces.IPaymentGateway;
import com.sadna.group13a.application.Interfaces.ITicketSupplier;
import com.sadna.group13a.application.Result;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for issue #245 — Ticket issuance failure and refund scenarios.
 *
 * <p>The checkout flow after a successful payment is:
 * <ol>
 *   <li>Issue tickets via {@link ITicketSupplier}.</li>
 *   <li>On success → save OrderHistory, delete cart, return receipt.</li>
 *   <li>On failure → call {@code refundPayment(transactionId)}, roll back seats,
 *       delete cart, return failure. OrderHistory is never saved.</li>
 * </ol>
 *
 * <p>Every test uses:
 * <ul>
 *   <li>Real in-memory repositories so that seat state, history records, and
 *       cart presence can be verified after the checkout call completes.</li>
 *   <li>Real {@link CheckoutDomainService} so the full seat lifecycle
 *       (HELD → SOLD → AVAILABLE via rollback) executes as in production.</li>
 *   <li>{@code Mockito.mock(IPaymentGateway.class)} — payment always succeeds
 *       (to reach the ticket step) except in the double-failure test.</li>
 *   <li>{@code Mockito.mock(ITicketSupplier.class)} — configured per test.</li>
 * </ul>
 *
 * <p>No Spring context is loaded.
 */
@DisplayName("[V3] #245 — Ticket issuance failure and refund scenarios")
class TicketIssuanceRobustnessTest {

    // ── Stable identifiers ─────────────────────────────────────────────────────

    private static final String USER_ID      = "user-ti-001";
    private static final String COMPANY_ID   = "company-ti-001";
    private static final String EVENT_ID     = "event-ti-001";
    private static final String ZONE_ID      = "zone-ti-001";
    private static final String SEAT_ID      = "seat-ti-001";
    private static final String SEAT_LABEL   = "C-12";
    private static final double SEAT_PRICE   = 95.0;
    private static final String VALID_TOKEN  = "ticket-robustness-test-token";
    private static final String TRANSACTION_ID = "TXN-TICKET-TEST-001";

    // ── Real in-memory repositories ────────────────────────────────────────────

    private ActiveOrderRepositoryImpl  orderRepo;
    private OrderHistoryRepositoryImpl historyRepo;
    private EventRepositoryImpl        eventRepo;
    private CompanyRepositoryImpl      companyRepo;
    private QueueRepositoryImpl        queueRepo;
    private RaffleRepositoryImpl       raffleRepo;
    private UserRepositoryImpl         userRepo;

    // ── Mockito mocks for both external ports ─────────────────────────────────

    private IPaymentGateway paymentGateway;
    private ITicketSupplier ticketSupplier;
    private SystemLogService systemLogService;

    // ── Lightweight test double for auth ──────────────────────────────────────

    private StubAuth auth;

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
        userRepo    = new UserRepositoryImpl(new FakeUserJpaRepository());

        checkoutDomainService        = new CheckoutDomainService();
        ticketingAccessDomainService = new TicketingAccessDomainService();
        cartDomainService            = new CartDomainService();

        paymentGateway = mock(IPaymentGateway.class);
        ticketSupplier = mock(ITicketSupplier.class);
        systemLogService = mock(SystemLogService.class);

        auth = new StubAuth(USER_ID, VALID_TOKEN);

        ApplicationEventPublisher noOpPublisher = event -> {};

        orderService = new OrderService(
                orderRepo,                    // 1. IActiveOrderRepository
                historyRepo,                  // 2. IOrderHistoryRepository
                eventRepo,                    // 3. IEventRepository
                companyRepo,                  // 4. ICompanyRepository
                queueRepo,                    // 5. IQueueRepository
                raffleRepo,                   // 6. IRaffleRepository
                paymentGateway,               // 7. IPaymentGateway  ← mocked
                ticketSupplier,               // 8. ITicketSupplier  ← mocked
                userRepo,                     // 9. IUserRepository
                auth,                         // 10. IAuth
                checkoutDomainService,        // 11. CheckoutDomainService
                ticketingAccessDomainService, // 12. TicketingAccessDomainService
                noOpPublisher,                // 13. ApplicationEventPublisher
                cartDomainService,            // 14. CartDomainService
                null,                         // 15. QueueService (not exercised here)
                systemLogService              // 16. SystemLogService
        );

        // Payment always succeeds with a fixed transaction ID unless a test overrides it.
        when(paymentGateway.processPayment(anyDouble(), anyString()))
                .thenReturn(Result.success(TRANSACTION_ID));
        when(paymentGateway.refundPayment(anyString()))
                .thenReturn(Result.success());

        seedEventCompanyAndUser();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Seed helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void seedEventCompanyAndUser() {
        companyRepo.save(new ProductionCompany(
                COMPANY_ID, "Ticket-Failure Events Ltd.", "Unit-test company", USER_ID));

        Seat       seat = new Seat(SEAT_ID, SEAT_LABEL);
        SeatedZone zone = new SeatedZone(ZONE_ID, "Front Row", SEAT_PRICE, List.of(seat));
        VenueMap   vm   = new VenueMap("vm-ti-001", "Robustness Arena", List.of(zone));

        Event event = new Event(
                EVENT_ID, "Ticket-Failure Test Concert", "desc",
                COMPANY_ID, LocalDateTime.now().plusDays(14), "Test");
        event.setVenueMap(vm);
        event.publish();
        eventRepo.save(event);

        userRepo.save(new Member(USER_ID, "ticketRobustnessUser", "hash-irrelevant"));
    }

    /**
     * Adds the seeded seat to the user's cart and returns the active-order ID.
     * Fails fast with a meaningful message if the setup itself is broken.
     */
    private String placeItemInCart() {
        Result<String> addResult = orderService.addItemToCart(VALID_TOKEN, EVENT_ID, ZONE_ID, SEAT_ID);
        assertTrue(addResult.isSuccess(),
                "Test setup: addItemToCart must succeed; actual error: "
                        + addResult.getErrorMessage());
        return addResult.getData().orElseThrow();
    }

    /** Reads the single seat from the (real) event repository. */
    private Seat readSeatFromRepo() {
        Event persistedEvent = eventRepo.findById(EVENT_ID).orElseThrow();
        SeatedZone persistedZone = (SeatedZone) persistedEvent.getZoneById(ZONE_ID);
        return persistedZone.findSeatById(SEAT_ID).orElseThrow();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test cases — ticket returns Result.failure (no exception thrown)
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Ticket supplier returns a failure result (no exception)")
    class TicketSupplierReturnsFailure {

        @Test
        @DisplayName("checkout_ticketIssuanceReturnsMinusOne_refundIsCalledAndStateRollsBack: "
                + "failure code from supplier → payment refunded, seats rolled back")
        void checkout_ticketIssuanceReturnsMinusOne_refundIsCalledAndStateRollsBack() {
            // Supplier failure maps to the '-1 / rejected' code from the external system.
            when(ticketSupplier.issueTickets(anyString(), anyList()))
                    .thenReturn(Result.failure("ticket issuance rejected (-1)"));

            String cartId = placeItemInCart();
            Result<OrderHistoryDTO> result =
                    orderService.executeCheckout(VALID_TOKEN, cartId, null, null, "card");

            // Checkout must fail.
            assertFalse(result.isSuccess(),
                    "Checkout must fail when the ticket supplier rejects issuance");
            assertTrue(result.getErrorMessage().contains("Ticket issuance failed"),
                    "Error message must state that ticket issuance failed");

            // Refund must have been triggered with the correct transaction ID.
            verify(paymentGateway, times(1)).refundPayment(TRANSACTION_ID);

            // Seats must be released — no dangling SOLD inventory.
            assertEquals(SeatStatus.AVAILABLE, readSeatFromRepo().getEffectiveStatus(),
                    "Seat must revert to AVAILABLE after ticket-failure rollback");

            // No history record must have been persisted.
            assertTrue(historyRepo.findByUserId(USER_ID).isEmpty(),
                    "No OrderHistory must be saved when ticket issuance fails");
        }

        @Test
        @DisplayName("checkout_ticketIssuanceTimesOut_refundIsCalledAndSeatsReleased: "
                + "timeout failure from supplier → payment refunded, seats released")
        void checkout_ticketIssuanceTimesOut_refundIsCalledAndSeatsReleased() {
            when(ticketSupplier.issueTickets(anyString(), anyList()))
                    .thenReturn(Result.failure("Ticket service timeout — no response after 30s"));

            String cartId = placeItemInCart();
            Result<OrderHistoryDTO> result =
                    orderService.executeCheckout(VALID_TOKEN, cartId, null, null, "card");

            assertFalse(result.isSuccess(),
                    "Checkout must fail when the ticket supplier times out");

            // A charged customer must always receive their money back.
            verify(paymentGateway, times(1)).refundPayment(TRANSACTION_ID);

            assertEquals(SeatStatus.AVAILABLE, readSeatFromRepo().getEffectiveStatus(),
                    "Seat must be AVAILABLE after a timeout-induced ticket rollback");

            assertTrue(historyRepo.findByUserId(USER_ID).isEmpty(),
                    "No OrderHistory must be saved when ticket issuance times out");
        }

        @Test
        @DisplayName("Refund must be called exactly once — no duplicate refund attempts")
        void checkout_ticketIssuanceFails_refundCalledExactlyOnce() {
            when(ticketSupplier.issueTickets(anyString(), anyList()))
                    .thenReturn(Result.failure("Service unavailable"));

            String cartId = placeItemInCart();
            orderService.executeCheckout(VALID_TOKEN, cartId, null, null, "card");

            // refundPayment must be called with the exact transaction ID, not a guess.
            verify(paymentGateway, times(1)).refundPayment(TRANSACTION_ID);
            // refundPartial must never be called — a full refund is required.
            verify(paymentGateway, never()).refundPartial(anyString(), anyDouble());
        }

        @Test
        @DisplayName("No OrderHistory must be created when ticket issuance fails")
        void checkout_ticketIssuanceFails_noOrderHistoryCreated() {
            when(ticketSupplier.issueTickets(anyString(), anyList()))
                    .thenReturn(Result.failure("Quota exceeded"));

            String cartId = placeItemInCart();
            orderService.executeCheckout(VALID_TOKEN, cartId, null, null, "card");

            assertTrue(historyRepo.findByUserId(USER_ID).isEmpty(),
                    "OrderHistory must only be saved after successful ticket issuance — "
                            + "it must not exist when ticket issuance fails");
        }

        @Test
        @DisplayName("Cart must be removed even on ticket failure — "
                + "seats are refunded and the order is voided, so the cart has no valid state")
        void checkout_ticketIssuanceFails_cartIsDeleted() {
            when(ticketSupplier.issueTickets(anyString(), anyList()))
                    .thenReturn(Result.failure("Supplier unreachable"));

            String cartId = placeItemInCart();
            orderService.executeCheckout(VALID_TOKEN, cartId, null, null, "card");

            // Unlike payment failure (where the cart survives for retry), ticket failure
            // voids the entire order — the cart must be cleaned up.
            assertFalse(orderRepo.findById(cartId).isPresent(),
                    "ActiveOrder must be deleted after ticket issuance failure "
                            + "(the order is voided; cart has no valid retry state)");
            assertTrue(orderRepo.findActiveByUserId(USER_ID).isEmpty(),
                    "User must have no active cart after the order is voided");
        }

        @Test
        @DisplayName("issueTickets must be called exactly once — no internal retry loop")
        void checkout_ticketIssuanceFails_ticketSupplierCalledExactlyOnce() {
            when(ticketSupplier.issueTickets(anyString(), anyList()))
                    .thenReturn(Result.failure("Rejected"));

            String cartId = placeItemInCart();
            orderService.executeCheckout(VALID_TOKEN, cartId, null, null, "card");

            verify(ticketSupplier, times(1)).issueTickets(anyString(), argThat(list -> list.size() == 1));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Double-failure: ticket fails AND refund fails
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Double failure: ticket issuance fails AND payment refund also fails")
    class DoubleFailure {

        /**
         * Verifies the double-failure scenario: ticket issuance fails, and when the
         * system tries to refund the already-charged payment, the refund gateway also
         * fails.
         *
         * <p>Expected invariants regardless of refund success:
         * <ul>
         *   <li>Checkout returns {@code Result.failure}.</li>
         *   <li>No {@link com.sadna.group13a.domain.Aggregates.OrderHistory.OrderHistory}
         *       is created (money was lost, but we must not lie about receipt).</li>
         *   <li>The refund attempt was made (we tried to give the money back).</li>
         *   <li>Cart is removed (order is voided either way).</li>
         * </ul>
         *
         * <p><strong>Note on "adminAlertIsLogged":</strong> the current implementation
         * does not check the return value of {@code refundPayment()} — a refund failure
         * is silently swallowed. Issue #243 will add explicit handling (logging and/or
         * a notification) for this double-failure case. Once implemented, add a
         * {@code verify()} assertion here that a warning notification or log-level ERROR
         * was triggered.
         */
        @Test
        @DisplayName("checkout_ticketIssuanceFails_refundFails_adminAlertIsLogged: "
                + "when both ticket and refund fail, checkout still returns failure "
                + "and refund was at least attempted")
        void checkout_ticketIssuanceFails_refundFails_adminAlertIsLogged() {
            // Both external systems fail.
            when(ticketSupplier.issueTickets(anyString(), anyList()))
                    .thenReturn(Result.failure("Ticket system completely down"));
            when(paymentGateway.refundPayment(TRANSACTION_ID))
                    .thenReturn(Result.failure("Refund gateway unreachable"));

            String cartId = placeItemInCart();
            Result<OrderHistoryDTO> result =
                    orderService.executeCheckout(VALID_TOKEN, cartId, null, null, "card");

            // Checkout must still return failure — never succeed when tickets weren't issued.
            assertFalse(result.isSuccess(),
                    "Checkout must fail even when the refund also fails");

            // The system must have *attempted* the refund — we tried to make the customer whole.
            verify(paymentGateway, times(1)).refundPayment(TRANSACTION_ID);

            // No history record — we cannot issue a receipt for an order without tickets.
            assertTrue(historyRepo.findByUserId(USER_ID).isEmpty(),
                    "No OrderHistory must be created when ticket issuance fails, "
                            + "regardless of whether the refund succeeded");

            // Cart must be cleaned up — the order is void.
            assertFalse(orderRepo.findById(cartId).isPresent(),
                    "Cart must be removed even when both ticket issuance and refund fail");

            // An admin-visible error log entry must record the failed refund.
            verify(systemLogService).logError(contains("REFUND FAILED"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Exception thrown by ticket supplier
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Ticket supplier throws a RuntimeException")
    class TicketSupplierThrowsException {

        /**
         * Verifies that when the ticket supplier throws an unchecked exception
         * (e.g., TCP connection reset, deserialization error), the checkout call
         * does not propagate that exception to the caller, the payment is refunded,
         * and no OrderHistory is persisted.
         *
         * <p>{@code OrderService.issueTickets} wraps the supplier call in a try-catch
         * and converts any {@code RuntimeException} into a {@code TicketIssuanceException},
         * which {@code executeCheckout} catches and handles the same way as a
         * {@code Result.failure} (issue #243).
         */
        @Test
        @DisplayName("checkout_ticketIssuanceThrows_refundIsCalledAndNoOrderHistoryCreated: "
                + "RuntimeException from supplier must not propagate; refund must be triggered")
        void checkout_ticketIssuanceThrows_refundIsCalledAndNoOrderHistoryCreated() {
            when(ticketSupplier.issueTickets(anyString(), anyList()))
                    .thenThrow(new RuntimeException("Ticket service connection reset by peer"));

            String cartId = placeItemInCart();

            // executeCheckout must catch the exception and return Result.failure.
            Result<OrderHistoryDTO> result = assertDoesNotThrow(
                    () -> orderService.executeCheckout(VALID_TOKEN, cartId, null, null, "card"),
                    "executeCheckout must catch RuntimeException from the ticket supplier "
                            + "and return Result.failure instead of throwing");

            assertFalse(result.isSuccess(),
                    "Checkout must return failure when the ticket supplier throws");

            // A charged customer must receive a refund even when the failure was an exception.
            verify(paymentGateway, atLeastOnce()).refundPayment(TRANSACTION_ID);

            // No history must be created — tickets were never issued.
            assertTrue(historyRepo.findByUserId(USER_ID).isEmpty(),
                    "No OrderHistory must be persisted when ticket issuance threw an exception");

            // Seat must not remain permanently SOLD.
            assertNotEquals(SeatStatus.SOLD, readSeatFromRepo().getEffectiveStatus(),
                    "Seat must not remain SOLD after ticket supplier threw an exception");
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

        @Override public String generateToken(String uid)    { return validToken; }
        @Override public boolean validateToken(String token)  { return validToken.equals(token); }
        @Override public String extractUserId(String token)   { return userId; }
    }
}
