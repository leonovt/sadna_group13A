package com.sadna.group13a.application.Services;

import com.sadna.group13a.application.DTO.OrderHistoryDTO;
import com.sadna.group13a.application.EventListeners.NotificationEventListener;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Interfaces.INotificationService;
import com.sadna.group13a.application.Interfaces.IPaymentGateway;
import com.sadna.group13a.application.Interfaces.ITicketSupplier;
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
import com.sadna.group13a.domain.Events.CompanyClosedByAdminEvent;
import com.sadna.group13a.domain.Events.OrderCompletedEvent;
import com.sadna.group13a.domain.Events.QueueTurnArrivedEvent;
import com.sadna.group13a.domain.Events.RaffleDrawnEvent;
import com.sadna.group13a.domain.Events.UserBannedEvent;
import com.sadna.group13a.infrastructure.RepositoryImpl.ActiveOrderRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.CompanyRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.EventRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.OrderHistoryRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.QueueRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.RaffleRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.UserRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.FakeUserJpaRepository;
import com.sadna.group13a.infrastructure.config.PersistenceConfig;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.FakeEventJpaRepository;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.FakeRaffleJpaRepository;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.FakeOrderHistoryJpaRepository;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.FakeActiveOrderJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link OrderService}.
 *
 * <p><strong>Philosophy:</strong> Every layer from Application down to Infrastructure
 * is exercised with real, concrete objects.  No Mockito mocks are used for internal
 * collaborators.  Only the external-boundary ports (IAuth, IPaymentGateway,
 * INotificationService) are replaced with deterministic test doubles implemented
 * right here in this file.
 *
 * <p>Spring context is NOT loaded — the service is wired by hand so that the tests
 * run fast and are free of framework magic.
 */
class OrderServiceIntegrationTest {

    // ── Stable identifiers shared across test helpers ─────────────────────────────

    private static final String USER_ID     = "user-integration-001";
    private static final String COMPANY_ID  = "company-integration-001";
    private static final String EVENT_ID    = "event-integration-001";
    private static final String ZONE_ID     = "zone-vip-001";
    private static final String SEAT_ID     = "seat-A1-001";
    private static final String SEAT_LABEL  = "A-1";
    private static final String ZONE_NAME   = "VIP Section";
    private static final double SEAT_PRICE  = 150.0;
    private static final String VALID_TOKEN = "integration-test-token-abc";

    // ── Fresh in-memory repositories (re-created in @BeforeEach) ─────────────────

    private ActiveOrderRepositoryImpl  orderRepo;
    private OrderHistoryRepositoryImpl historyRepo;
    private EventRepositoryImpl        eventRepo;
    private CompanyRepositoryImpl      companyRepo;
    private QueueRepositoryImpl        queueRepo;
    private RaffleRepositoryImpl       raffleRepo;
    private UserRepositoryImpl         userRepo;

    // ── Real domain services (stateless, safe to share) ──────────────────────────

    private CheckoutDomainService        checkoutDomainService;
    private TicketingAccessDomainService ticketingAccessDomainService;
    private CartDomainService            cartDomainService;

    // ── External-port test doubles ────────────────────────────────────────────────

    private SpyPaymentGateway      paymentGateway;
    private StubTicketSupplier     ticketSupplier;
    private SpyNotificationService notificationService;
    private StubAuth               auth;

    // ── System Under Test ─────────────────────────────────────────────────────────

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        // Each test starts with a completely clean in-memory store.
        orderRepo   = new ActiveOrderRepositoryImpl(new FakeActiveOrderJpaRepository(), new PersistenceConfig().domainObjectMapper());
        historyRepo = new OrderHistoryRepositoryImpl(new FakeOrderHistoryJpaRepository(), new PersistenceConfig().domainObjectMapper());
        eventRepo   = new EventRepositoryImpl(new FakeEventJpaRepository(), new PersistenceConfig().domainObjectMapper());
        companyRepo = new CompanyRepositoryImpl();
        queueRepo   = new QueueRepositoryImpl();
        raffleRepo  = new RaffleRepositoryImpl(new FakeRaffleJpaRepository(), new PersistenceConfig().domainObjectMapper());
        userRepo    = new UserRepositoryImpl(new FakeUserJpaRepository(), new PersistenceConfig().domainObjectMapper());

        checkoutDomainService        = new CheckoutDomainService();
        ticketingAccessDomainService = new TicketingAccessDomainService();
        cartDomainService            = new CartDomainService();

        paymentGateway      = new SpyPaymentGateway();
        ticketSupplier      = new StubTicketSupplier();
        notificationService = new SpyNotificationService();
        auth                = new StubAuth(USER_ID, VALID_TOKEN);

        // Bridge Spring's ApplicationEventPublisher to the real NotificationEventListener
        // without loading a Spring application context.  Events published by OrderService
        // flow through the listener and land in the SpyNotificationService, letting us
        // assert the full notification chain.
        NotificationEventListener notifListener = new NotificationEventListener(notificationService);
        ApplicationEventPublisher eventPublisher = event -> {
            if (event instanceof OrderCompletedEvent e)        notifListener.onOrderCompleted(e);
            else if (event instanceof QueueTurnArrivedEvent e) notifListener.onQueueTurnArrived(e);
            else if (event instanceof UserBannedEvent e)       notifListener.onUserBanned(e);
            else if (event instanceof CompanyClosedByAdminEvent e) notifListener.onCompanyClosed(e);
            else if (event instanceof RaffleDrawnEvent e)      notifListener.onRaffleDrawn(e);
        };

        orderService = new OrderService(
                        orderRepo,                    // 1. IActiveOrderRepository
                        historyRepo,                  // 2. IOrderHistoryRepository
                        eventRepo,                    // 3. IEventRepository
                        companyRepo,                  // 4. ICompanyRepository
                        queueRepo,                    // 5. IQueueRepository
                        raffleRepo,                   // 6. IRaffleRepository
                        paymentGateway,               // 7. IPaymentGateway
                        ticketSupplier,               // 8. ITicketSupplier
                        userRepo,                     // 9. IUserRepository
                        auth,                         // 10. IAuth
                        checkoutDomainService,        // 11. CheckoutDomainService
                        ticketingAccessDomainService, // 12. TicketingAccessDomainService
                        eventPublisher,               // 13. ApplicationEventPublisher
                        cartDomainService,            // 14. CartDomainService
                        null                          // 15. QueueService (queue advancement not under test here)
                );

        seedEventAndCompany();
        seedUser();
    }

    // ── Seed helpers ──────────────────────────────────────────────────────────────

    /**
     * Persists a published Event (with a VenueMap containing one SeatedZone
     * and one available Seat) plus its owning ProductionCompany.
     */
    private void seedEventAndCompany() {
        ProductionCompany company = new ProductionCompany(
                COMPANY_ID, "Integration Events Ltd.", "Concerts and live shows", USER_ID);
        companyRepo.save(company);

        Seat      seat     = new Seat(SEAT_ID, SEAT_LABEL);
        SeatedZone zone    = new SeatedZone(ZONE_ID, ZONE_NAME, SEAT_PRICE, List.of(seat));
        VenueMap  venueMap = new VenueMap("vm-001", "The Big Arena", List.of(zone));

        Event event = new Event(
                EVENT_ID,
                "Grand Integration Concert",
                "A fully wired-up integration-test event",
                COMPANY_ID,
                LocalDateTime.now().plusDays(30),
                "Music");
        event.setVenueMap(venueMap);  // must be set before publishing
        event.publish();

        eventRepo.save(event);
    }

    /** Persists the buyer whose token maps to {@code USER_ID}. */
    private void seedUser() {
        userRepo.save(new Member(USER_ID, "integrationUser", "hashed-pw-irrelevant-in-this-context"));
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Primary scenario: full end-to-end checkout
    // ─────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Full end-to-end checkout flow")
    class EndToEndCheckoutTests {

        @Test
        @DisplayName("Given a seeded event and active user, "
                + "when the user adds a seat and checks out, "
                + "then all layers communicate correctly")
        void givenSeededEventAndUser_whenAddItemThenCheckout_thenAllLayersCommunicateCorrectly() {

            // ── GIVEN ─────────────────────────────────────────────────────────────
            // Phase 1: user adds the seat to their cart.
            // addItemToCart holds the seat on the domain aggregate and persists the cart.
            Result<String> addResult =
                    orderService.addItemToCart(VALID_TOKEN, EVENT_ID, ZONE_ID, SEAT_ID);

            assertTrue(addResult.isSuccess(),
                    "addItemToCart must succeed: event is published and seat is available");
            String activeOrderId = addResult.getData().orElseThrow();

            // Confirm the ActiveOrder exists in the repository before proceeding.
            assertTrue(orderRepo.findById(activeOrderId).isPresent(),
                    "ActiveOrder must be persisted by addItemToCart");

            // Confirm the seat is already in HELD state on the persisted event.
            SeatedZone zoneAfterAdd = (SeatedZone) eventRepo
                    .findById(EVENT_ID).orElseThrow()
                    .getZoneById(ZONE_ID);
            assertEquals(SeatStatus.HELD,
                    zoneAfterAdd.findSeatById(SEAT_ID).orElseThrow().getEffectiveStatus(),
                    "Seat must be HELD immediately after addItemToCart");

            // ── WHEN ──────────────────────────────────────────────────────────────
            // Phase 2: user executes the checkout — the service orchestrates:
            //   access validation → domain checkout → payment → persistence → notification.
            Result<OrderHistoryDTO> checkoutResult =
                    orderService.executeCheckout(VALID_TOKEN, activeOrderId, null, "card-4111-1111-1111-1111");

            // ── THEN ──────────────────────────────────────────────────────────────

            // Assertion 1 — the service returned a successful result with a populated DTO.
            assertTrue(checkoutResult.isSuccess(),
                    "executeCheckout must succeed end-to-end for a REGULAR event");
            OrderHistoryDTO receiptDTO = checkoutResult.getData().orElseThrow();
            assertNotNull(receiptDTO.receiptId(),
                    "Receipt DTO must carry a non-null receipt ID");
            assertEquals(USER_ID, receiptDTO.userId(),
                    "Receipt must be attributed to the buyer");
            assertEquals(SEAT_PRICE, receiptDTO.totalPaid(), 0.01,
                    "Total paid must equal the zone's base price (no discount applied)");
            assertEquals(1, receiptDTO.items().size(),
                    "Receipt must list exactly one purchased item");

            // Assertion 2 — the external PaymentGateway was invoked exactly once.
            assertEquals(1, paymentGateway.getProcessPaymentCallCount(),
                    "PaymentGateway.processPayment must be called exactly once during checkout");
            assertEquals(SEAT_PRICE, paymentGateway.getLastChargedAmount(), 0.01,
                    "The gateway must be charged the correct seat price");

            // Assertion 3 — the ActiveOrder was removed from the repository (cart cleaned up).
            assertTrue(orderRepo.findById(activeOrderId).isEmpty(),
                    "ActiveOrder must be deleted after successful checkout");
            assertTrue(orderRepo.findActiveByUserId(USER_ID).isEmpty(),
                    "User must have no active cart after checkout completes");

            // Assertion 4 — an immutable OrderHistory record was persisted.
            List<com.sadna.group13a.domain.Aggregates.OrderHistory.OrderHistory> histories =
                    historyRepo.findByUserId(USER_ID);
            assertEquals(1, histories.size(),
                    "Exactly one OrderHistory record must be saved for the buyer");

            var savedHistory = histories.get(0);
            assertEquals(USER_ID, savedHistory.getUserId(),
                    "Persisted OrderHistory must belong to the correct user");
            assertEquals(SEAT_PRICE, savedHistory.getTotalPaid(), 0.01,
                    "Persisted total must match the checkout amount");
            assertEquals(1, savedHistory.getItems().size(),
                    "Persisted history must contain exactly one line item");

            var savedItem = savedHistory.getItems().get(0);
            assertEquals(EVENT_ID,   savedItem.getEventId(),   "History item must reference the correct event");
            assertEquals(ZONE_NAME,  savedItem.getZoneName(),  "History item must carry the zone name");
            assertEquals(SEAT_LABEL, savedItem.getSeatLabel(), "History item must carry the seat label");
            assertEquals(COMPANY_ID, savedItem.getCompanyId(), "History item must reference the owning company");

            // Assertion 5 — the seat transitioned HELD → SOLD inside the persisted Event aggregate.
            Event persistedEvent = eventRepo.findById(EVENT_ID).orElseThrow();
            SeatedZone persistedZone = (SeatedZone) persistedEvent.getZoneById(ZONE_ID);
            Seat persistedSeat = persistedZone.findSeatById(SEAT_ID).orElseThrow();
            assertEquals(SeatStatus.SOLD, persistedSeat.getStatus(),
                    "Seat must be SOLD in the persisted Event aggregate after checkout");

            // Assertion 6 — the NotificationService received the order-completed notification
            // via the full chain: OrderService → ApplicationEventPublisher →
            //                     NotificationEventListener → SpyNotificationService.
            assertEquals(1, notificationService.getOrderCompletedCallCount(),
                    "NotificationService.notifyOrderCompleted must be called exactly once");
            assertEquals(USER_ID, notificationService.getLastNotifiedUserId(),
                    "Notification must be addressed to the buyer");
            assertNotNull(notificationService.getLastReceiptId(),
                    "Notification must include the receipt ID");
            assertEquals(SEAT_PRICE, notificationService.getLastTotalPaid(), 0.01,
                    "Notification must report the amount actually charged");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Secondary scenarios: guard rails and sad paths
    // ─────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("addItemToCart — authentication and availability guards")
    class AddItemToCartTests {

        @Test
        @DisplayName("Given an invalid token, addItemToCart returns failure without mutating state")
        void givenInvalidToken_whenAddItemToCart_thenFailureAndNoCartCreated() {
            Result<String> result =
                    orderService.addItemToCart("INVALID-TOKEN", EVENT_ID, ZONE_ID, SEAT_ID);

            assertFalse(result.isSuccess(),
                    "addItemToCart must reject an invalid token");
            assertTrue(orderRepo.findActiveByUserId(USER_ID).isEmpty(),
                    "No cart must be created when authentication fails");
        }

        @Test
        @DisplayName("Given a non-existent event ID, addItemToCart returns failure")
        void givenNonExistentEvent_whenAddItemToCart_thenFailure() {
            Result<String> result =
                    orderService.addItemToCart(VALID_TOKEN, "no-such-event", ZONE_ID, SEAT_ID);

            assertFalse(result.isSuccess(),
                    "addItemToCart must fail when the event does not exist");
        }

        @Test
        @DisplayName("Given a seat already held by another user, addItemToCart returns failure")
        void givenAlreadyHeldSeat_whenAddItemToCart_thenFailure() {
            // Pre-hold the seat as a different user, via Event's own facade so version tracks it.
            Event event = eventRepo.findById(EVENT_ID).orElseThrow();
            event.reserveSeat(ZONE_ID, SEAT_ID, "other-user-999");
            eventRepo.save(event);

            Result<String> result =
                    orderService.addItemToCart(VALID_TOKEN, EVENT_ID, ZONE_ID, SEAT_ID);

            assertFalse(result.isSuccess(),
                    "addItemToCart must fail when the target seat is already held");
            assertTrue(orderRepo.findActiveByUserId(USER_ID).isEmpty(),
                    "No cart must be created when the seat hold fails");
        }
    }

    @Nested
    @DisplayName("cancelCart — releases holds and removes the cart")
    class CancelCartTests {

        @Test
        @DisplayName("cancelCart releases the seat hold and removes the ActiveOrder")
        void givenCartWithHeldSeat_whenCancelCart_thenSeatReleasedAndCartRemoved() {
            // GIVEN: add an item (this holds the seat and persists the cart)
            Result<String> addResult =
                    orderService.addItemToCart(VALID_TOKEN, EVENT_ID, ZONE_ID, SEAT_ID);
            assertTrue(addResult.isSuccess());
            String activeOrderId = addResult.getData().orElseThrow();

            SeatedZone zoneBeforeCancel = (SeatedZone) eventRepo
                    .findById(EVENT_ID).orElseThrow().getZoneById(ZONE_ID);
            assertEquals(SeatStatus.HELD,
                    zoneBeforeCancel.findSeatById(SEAT_ID).orElseThrow().getEffectiveStatus(),
                    "Seat must be HELD before cancellation");

            // WHEN
            Result<Void> cancelResult = orderService.cancelCart(VALID_TOKEN);

            // THEN
            assertTrue(cancelResult.isSuccess(), "cancelCart must succeed");
            assertTrue(orderRepo.findById(activeOrderId).isEmpty(),
                    "ActiveOrder must be removed after cancellation");

            SeatedZone zoneAfterCancel = (SeatedZone) eventRepo
                    .findById(EVENT_ID).orElseThrow().getZoneById(ZONE_ID);
            assertEquals(SeatStatus.AVAILABLE,
                    zoneAfterCancel.findSeatById(SEAT_ID).orElseThrow().getEffectiveStatus(),
                    "Seat must return to AVAILABLE after the cart is cancelled");

            assertEquals(0, notificationService.getOrderCompletedCallCount(),
                    "No order-completed notification must fire on a cancelled cart");
        }

        @Test
        @DisplayName("cancelCart succeeds gracefully when the user has no active cart")
        void givenNoActiveCart_whenCancelCart_thenSucceedsGracefully() {
            Result<Void> result = orderService.cancelCart(VALID_TOKEN);

            assertTrue(result.isSuccess(),
                    "cancelCart must be idempotent when there is no active cart");
        }
    }

    @Nested
    @DisplayName("executeCheckout — sad paths that must not persist history")
    class CheckoutSadPathTests {

        @Test
        @DisplayName("Given an invalid token, checkout fails and no history is persisted")
        void givenInvalidToken_whenExecuteCheckout_thenFailureAndNoHistory() {
            Result<OrderHistoryDTO> result =
                    orderService.executeCheckout("INVALID-TOKEN", "any-order-id", null, "card");

            assertFalse(result.isSuccess(),
                    "Checkout must reject an invalid token");
            assertTrue(historyRepo.findAll().isEmpty(),
                    "No OrderHistory must be created on an authentication failure");
            assertEquals(0, notificationService.getOrderCompletedCallCount(),
                    "No notification must be dispatched on an authentication failure");
        }

        @Test
        @DisplayName("Given an empty cart, checkout fails and no history is persisted")
        void givenEmptyCart_whenExecuteCheckout_thenFailureAndNoHistory() {
            // Manually persist an empty ActiveOrder (bypassing addItemToCart).
            ActiveOrder emptyCart = new ActiveOrder(UUID.randomUUID().toString(), USER_ID);
            orderRepo.save(emptyCart);

            Result<OrderHistoryDTO> result =
                    orderService.executeCheckout(VALID_TOKEN, emptyCart.getId(), null, "card");

            assertFalse(result.isSuccess(),
                    "Checkout must fail for an empty cart");
            assertTrue(historyRepo.findByUserId(USER_ID).isEmpty(),
                    "No OrderHistory must be persisted when the cart has no items");
            assertEquals(0, paymentGateway.getProcessPaymentCallCount(),
                    "PaymentGateway must not be invoked when the cart is empty");
            assertEquals(0, notificationService.getOrderCompletedCallCount(),
                    "No notification must fire for a failed checkout");
        }

        @Test
        @DisplayName("Given a cart belonging to a different user, checkout fails")
        void givenCartOwnedByOtherUser_whenExecuteCheckout_thenFailure() {
            // Persist a cart that belongs to a different user.
            ActiveOrder otherCart = new ActiveOrder(UUID.randomUUID().toString(), "other-user-456");
            orderRepo.save(otherCart);

            Result<OrderHistoryDTO> result =
                    orderService.executeCheckout(VALID_TOKEN, otherCart.getId(), null, "card");

            assertFalse(result.isSuccess(),
                    "Checkout must reject a cart that does not belong to the authenticated user");
        }

        @Test
        @DisplayName("Given a declined payment, checkout fails and no history is persisted")
        void givenDeclinedPayment_whenExecuteCheckout_thenFailureAndNoHistory() {
            // Arm the spy gateway to decline the next payment.
            paymentGateway.setNextPaymentDeclined(true);

            // Add item — this succeeds and holds the seat.
            Result<String> addResult =
                    orderService.addItemToCart(VALID_TOKEN, EVENT_ID, ZONE_ID, SEAT_ID);
            assertTrue(addResult.isSuccess());
            String activeOrderId = addResult.getData().orElseThrow();

            // WHEN: attempt checkout with a gateway that will decline.
            Result<OrderHistoryDTO> checkoutResult =
                    orderService.executeCheckout(VALID_TOKEN, activeOrderId, null, "bad-card");

            // THEN
            assertFalse(checkoutResult.isSuccess(),
                    "Checkout must fail when the payment gateway declines");
            assertTrue(checkoutResult.getErrorMessage().contains("Payment declined"),
                    "Error message must indicate payment failure");
            assertTrue(historyRepo.findByUserId(USER_ID).isEmpty(),
                    "No OrderHistory must be persisted when payment is declined");
            assertEquals(0, notificationService.getOrderCompletedCallCount(),
                    "No notification must fire when payment is declined");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Test infrastructure — external-boundary doubles
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * A deterministic {@link IAuth} stub that validates a single pre-configured
     * token/userId pair.  Allows integration tests to bypass JJWT signature logic
     * while keeping the auth boundary real from the service's perspective.
     */
    static class StubAuth implements IAuth {

        private final String userId;
        private final String validToken;

        StubAuth(String userId, String validToken) {
            this.userId     = userId;
            this.validToken = validToken;
        }

        @Override public String generateToken(String uid) { return validToken; }

        @Override public boolean validateToken(String token) { return validToken.equals(token); }

        @Override public String extractUserId(String token)  { return userId; }
    }

    /**
     * A recording {@link IPaymentGateway} that delegates to the real
     * {@link com.sadna.group13a.infrastructure.StubPaymentGateway} but also tracks
     * every call so tests can assert that the gateway was actually invoked.
     *
     * <p>Set {@code nextPaymentDeclined = true} to simulate a declined card on the
     * very next call to {@code processPayment}.
     */
    static class SpyPaymentGateway implements IPaymentGateway {

        private final AtomicInteger processPaymentCallCount = new AtomicInteger(0);
        private volatile double  lastChargedAmount;
        private volatile boolean nextPaymentDeclined = false;

        @Override
        public boolean isConnected() { return true; }

        @Override
        public Result<String> processPayment(double amount, String paymentDetails) {
            processPaymentCallCount.incrementAndGet();
            lastChargedAmount = amount;
            if (nextPaymentDeclined) {
                nextPaymentDeclined = false;  // reset after one use
                return Result.failure("Card declined by issuing bank");
            }
            return Result.success("TXN-" + UUID.randomUUID());
        }

        @Override
        public Result<Void> refundPayment(String transactionId) { return Result.success(); }

        @Override
        public Result<Void> refundPartial(String transactionId, double amount) { return Result.success(); }

        // ── Accessors ─────────────────────────────────────────────────────────────
        int    getProcessPaymentCallCount() { return processPaymentCallCount.get(); }
        double getLastChargedAmount()       { return lastChargedAmount; }
        void   setNextPaymentDeclined(boolean declined) { this.nextPaymentDeclined = declined; }
    }

    /**
     * A capturing {@link INotificationService} that records invocations so tests
     * can assert on the full notification chain without a real messaging system.
     *
     * <p>Thread-safe so it can also be used in future concurrency tests.
     */
    static class StubTicketSupplier implements ITicketSupplier {
        @Override public boolean isConnected() { return true; }
        @Override public Result<List<String>> issueTickets(String orderId, int quantity) {
            List<String> codes = new java.util.ArrayList<>();
            for (int i = 0; i < quantity; i++) codes.add("TICKET-" + UUID.randomUUID());
            return Result.success(codes);
        }
        @Override public Result<Void> cancelTickets(List<String> ticketCodes) { return Result.success(); }
    }

    static class SpyNotificationService implements INotificationService {

        private final AtomicInteger orderCompletedCallCount = new AtomicInteger(0);
        private volatile String lastNotifiedUserId;
        private volatile String lastReceiptId;
        private volatile double lastTotalPaid;

        @Override
        public void notifyOrderCompleted(String userId, String receiptId, double totalPaid) {
            orderCompletedCallCount.incrementAndGet();
            this.lastNotifiedUserId = userId;
            this.lastReceiptId      = receiptId;
            this.lastTotalPaid      = totalPaid;
        }

        @Override public void notifyQueueTurnArrived(String u, String e, LocalDateTime t) {}
        @Override public void notifyUserBanned(String u, String adminId) {}
        @Override public void notifyUserSuspended(String u, java.time.LocalDateTime suspendedUntil) {}
        @Override public void notifyCompanyClosed(java.util.List<String> staffIds, String c, String adminId) {}
        @Override public void notifyRaffleDrawn(java.util.List<String> participantUserIds, String e, int w) {}
        @Override public void notifyActionFailed(String userId, String reason) {}
        @Override public void notifyCompanySuspended(java.util.List<String> staffIds, String companyId) {}
        @Override public void notifyCompanyReopened(java.util.List<String> staffIds, String companyId) {}
        @Override public void notifyStaffNominated(String userId, String companyId, String role) {}
        @Override public void notifyStaffRemoved(String userId, String companyId) {}
        @Override public void notifyPermissionsUpdated(String userId, String companyId) {}
        @Override public void notifyCartExpired(String userId) {}
        @Override public void notifyEventCancelled(java.util.List<String> buyerIds, String eventId, String eventTitle) {}
        @Override public void notifyRefundIssued(String userId, String receiptId, double amount, String eventTitle) {}
        @Override public void notifyEventRescheduled(java.util.List<String> buyerIds, String eventId, String eventTitle, LocalDateTime newDate) {}
        @Override public void notifyUserReactivated(String userId) {}
        @Override public void notifyEventSoldOut(java.util.List<String> staffIds, String eventId, String eventTitle) {}
        @Override public void notifyRaffleWon(String userId, String eventId, String authCode, LocalDateTime expiresAt) {}
        @Override public void notifyAdminMessage(String targetUserId, String message) {}

        // ── Accessors ─────────────────────────────────────────────────────────────
        int    getOrderCompletedCallCount() { return orderCompletedCallCount.get(); }
        String getLastNotifiedUserId()      { return lastNotifiedUserId; }
        String getLastReceiptId()           { return lastReceiptId; }
        double getLastTotalPaid()           { return lastTotalPaid; }
    }
}
