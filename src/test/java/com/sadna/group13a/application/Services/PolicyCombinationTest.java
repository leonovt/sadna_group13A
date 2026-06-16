package com.sadna.group13a.application.Services;

import com.sadna.group13a.application.DTO.OrderHistoryDTO;
import com.sadna.group13a.application.EventListeners.NotificationEventListener;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Interfaces.INotificationService;
import com.sadna.group13a.application.Interfaces.IPaymentGateway;
import com.sadna.group13a.application.Interfaces.ITicketSupplier;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import com.sadna.group13a.domain.Aggregates.Event.Event;
import com.sadna.group13a.domain.Aggregates.Event.StandingZone;
import com.sadna.group13a.domain.Aggregates.Event.VenueMap;
import com.sadna.group13a.domain.Aggregates.User.Member;
import com.sadna.group13a.domain.DomainServices.CartDomainService;
import com.sadna.group13a.domain.DomainServices.CheckoutDomainService;
import com.sadna.group13a.domain.DomainServices.TicketingAccessDomainService;
import com.sadna.group13a.domain.Events.OrderCompletedEvent;
import com.sadna.group13a.domain.policies.discount.ConditionalDiscount;
import com.sadna.group13a.domain.policies.discount.NoDiscountPolicy;
import com.sadna.group13a.domain.policies.discount.SimpleDiscount;
import com.sadna.group13a.domain.policies.purchase.AgeRestrictionPolicy;
import com.sadna.group13a.domain.policies.purchase.AndPolicy;
import com.sadna.group13a.domain.policies.purchase.MaxTicketsPolicy;
import com.sadna.group13a.domain.policies.purchase.MinTicketsPolicy;
import com.sadna.group13a.domain.policies.purchase.OrPolicy;
import com.sadna.group13a.domain.shared.DiscountPolicy;
import com.sadna.group13a.domain.shared.PurchasePolicy;
import com.sadna.group13a.infrastructure.RepositoryImpl.ActiveOrderRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.CompanyRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.EventRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.OrderHistoryRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.QueueRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.RaffleRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.UserRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests verifying how purchase and discount policies on events and
 * companies interact during checkout.
 *
 * Real in-memory repositories and domain services are used throughout; only the
 * external ports (auth, payment, notifications, ticket supplier) are test doubles.
 */
class PolicyCombinationTest {

    // ── Stable identifiers ────────────────────────────────────────────────────────

    private static final String USER_ID    = "policy-test-user";
    private static final String TOKEN      = "policy-test-token";
    private static final String COMPANY_ID = "policy-test-co";
    private static final String EVENT_ID   = "policy-test-ev";
    private static final String ZONE_ID    = "policy-test-zone";
    private static final double BASE_PRICE = 100.0;
    private static final int    CAPACITY   = 20;

    // ── Repositories ─────────────────────────────────────────────────────────────

    private ActiveOrderRepositoryImpl  orderRepo;
    private OrderHistoryRepositoryImpl historyRepo;
    private EventRepositoryImpl        eventRepo;
    private CompanyRepositoryImpl      companyRepo;
    private QueueRepositoryImpl        queueRepo;
    private RaffleRepositoryImpl       raffleRepo;
    private UserRepositoryImpl         userRepo;

    // ── Domain services ───────────────────────────────────────────────────────────

    private CheckoutDomainService        checkoutDomainService;
    private TicketingAccessDomainService ticketingAccessDomainService;
    private CartDomainService            cartDomainService;

    // ── SUT ───────────────────────────────────────────────────────────────────────

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderRepo   = new ActiveOrderRepositoryImpl();
        historyRepo = new OrderHistoryRepositoryImpl();
        eventRepo   = new EventRepositoryImpl();
        companyRepo = new CompanyRepositoryImpl();
        queueRepo   = new QueueRepositoryImpl();
        raffleRepo  = new RaffleRepositoryImpl();
        userRepo    = new UserRepositoryImpl();

        checkoutDomainService        = new CheckoutDomainService();
        ticketingAccessDomainService = new TicketingAccessDomainService();
        cartDomainService            = new CartDomainService();

        IPaymentGateway    payment      = new StubPaymentGateway();
        ITicketSupplier    tickets      = new StubTicketSupplier();
        INotificationService notifications = new NoOpNotificationService();
        IAuth              auth         = new StubAuth(USER_ID, TOKEN);

        NotificationEventListener listener = new NotificationEventListener(notifications);
        ApplicationEventPublisher  publisher = event -> {
            if (event instanceof OrderCompletedEvent e) listener.onOrderCompleted(e);
        };

        orderService = new OrderService(
                orderRepo, historyRepo, eventRepo, companyRepo, queueRepo, raffleRepo,
                payment, tickets, userRepo, auth,
                checkoutDomainService, ticketingAccessDomainService, publisher,
                cartDomainService, null);
    }

    // ── Seed helpers ──────────────────────────────────────────────────────────────

    private void seedCompany(PurchasePolicy purchasePolicy, DiscountPolicy discountPolicy) {
        ProductionCompany company = new ProductionCompany(COMPANY_ID, "Test Co", "Desc", USER_ID);
        if (purchasePolicy != null) company.setPurchasePolicy(purchasePolicy);
        if (discountPolicy != null) company.setDiscountPolicy(discountPolicy);
        companyRepo.save(company);
    }

    private void seedEvent(PurchasePolicy purchasePolicy, DiscountPolicy discountPolicy) {
        seedEventWithId(EVENT_ID, ZONE_ID, COMPANY_ID, purchasePolicy, discountPolicy);
    }

    private void seedEventWithId(String eventId, String zoneId, String companyId,
                                  PurchasePolicy purchasePolicy, DiscountPolicy discountPolicy) {
        StandingZone zone  = new StandingZone(zoneId, "GA", BASE_PRICE, CAPACITY);
        VenueMap     vm    = new VenueMap("vm-" + eventId, "Venue", List.of(zone));
        Event        event = new Event(eventId, "Policy Test Event", "Desc", companyId,
                                       LocalDateTime.now().plusDays(30), "Music");
        event.setVenueMap(vm);
        if (purchasePolicy != null) event.setPurchasePolicy(purchasePolicy);
        if (discountPolicy != null) event.setDiscountPolicy(discountPolicy);
        event.publish();
        eventRepo.save(event);
    }

    private void seedUser() {
        userRepo.save(new Member(USER_ID, "testuser", "hash"));
    }

    private String addTickets(int quantity) {
        return addTicketsForEvent(EVENT_ID, ZONE_ID, quantity);
    }

    private String addTicketsForEvent(String eventId, String zoneId, int quantity) {
        Result<String> result = orderService.addBatchItemsToCart(TOKEN, eventId, zoneId, null, quantity);
        assertTrue(result.isSuccess(), "addBatchItemsToCart should succeed but got: " + result.getErrorMessage());
        return result.getData().orElseThrow();
    }

    private Result<OrderHistoryDTO> checkout(String orderId) {
        return orderService.executeCheckout(TOKEN, orderId, null, "card-4242");
    }

    // ── Purchase policy: event-level ──────────────────────────────────────────────

    @Test
    @DisplayName("AllowAll on event and company: checkout succeeds")
    void allowAll_eventAndCompany_checkoutSucceeds() {
        seedCompany(null, null);
        seedEvent(null, null);
        seedUser();

        String orderId = addTickets(1);
        assertTrue(checkout(orderId).isSuccess());
    }

    @Test
    @DisplayName("Event MinTickets(2), buy 2: checkout succeeds")
    void eventMinTickets_buyingExactMinimum_checkoutSucceeds() {
        seedCompany(null, null);
        seedEvent(new MinTicketsPolicy(2), null);
        seedUser();

        String orderId = addTickets(2);
        assertTrue(checkout(orderId).isSuccess());
    }

    @Test
    @DisplayName("Event MinTickets(2), buy 1: checkout blocked by event policy")
    void eventMinTickets_buyingBelowMinimum_checkoutBlocked() {
        seedCompany(null, null);
        seedEvent(new MinTicketsPolicy(2), null);
        seedUser();

        String orderId = addTickets(1);
        Result<OrderHistoryDTO> result = checkout(orderId);
        assertFalse(result.isSuccess());
    }

    @Test
    @DisplayName("Event MaxTickets(2), buy 3: checkout blocked by event policy")
    void eventMaxTickets_buyingAboveMaximum_checkoutBlocked() {
        seedCompany(null, null);
        seedEvent(new MaxTicketsPolicy(2), null);
        seedUser();

        String orderId = addTickets(3);
        assertFalse(checkout(orderId).isSuccess());
    }

    @Test
    @DisplayName("Event AndPolicy(Min2, Max4), buy 3: within both bounds → succeeds")
    void eventAndPolicy_buyingWithinBothBounds_checkoutSucceeds() {
        seedCompany(null, null);
        seedEvent(new AndPolicy(new MinTicketsPolicy(2), new MaxTicketsPolicy(4)), null);
        seedUser();

        String orderId = addTickets(3);
        assertTrue(checkout(orderId).isSuccess());
    }

    @Test
    @DisplayName("Event AndPolicy(Min2, Max4), buy 1: below min → blocked by AND policy")
    void eventAndPolicy_buyingBelowMin_checkoutBlocked() {
        seedCompany(null, null);
        seedEvent(new AndPolicy(new MinTicketsPolicy(2), new MaxTicketsPolicy(4)), null);
        seedUser();

        String orderId = addTickets(1);
        assertFalse(checkout(orderId).isSuccess());
    }

    @Test
    @DisplayName("Event OrPolicy(AgeRestriction(99), MinTickets(1)), buy 1: MinTickets passes → OR succeeds")
    void eventOrPolicy_oneConditionMet_checkoutSucceeds() {
        // User has no date of birth → age = 0; AgeRestriction(99) fails.
        // MinTickets(1) passes. OR → overall passes.
        seedCompany(null, null);
        seedEvent(new OrPolicy(new AgeRestrictionPolicy(99), new MinTicketsPolicy(1)), null);
        seedUser();

        String orderId = addTickets(1);
        assertTrue(checkout(orderId).isSuccess());
    }

    // ── Purchase policy: company-level uses cross-event count ─────────────────────

    @Test
    @DisplayName("Company MinTickets(2), 1 ticket × 2 events in same company: total=2 → succeeds")
    void companyMinTickets_twoEvents_companyTotalMeetsMin_checkoutSucceeds() {
        String event2Id = "policy-test-ev-2";
        String zone2Id  = "policy-test-zone-2";

        ProductionCompany company = new ProductionCompany(COMPANY_ID, "Test Co", "Desc", USER_ID);
        company.setPurchasePolicy(new MinTicketsPolicy(2));
        companyRepo.save(company);

        seedEventWithId(EVENT_ID, ZONE_ID, COMPANY_ID, null, null);
        seedEventWithId(event2Id, zone2Id, COMPANY_ID, null, null);
        seedUser();

        String orderId = addTicketsForEvent(EVENT_ID, ZONE_ID, 1);
        addTicketsForEvent(event2Id, zone2Id, 1);  // appends to same cart

        Result<OrderHistoryDTO> result = checkout(orderId);
        assertTrue(result.isSuccess(),
                "company MinTickets(2) should pass when 1+1=2 tickets across two events");
    }

    @Test
    @DisplayName("Company MinTickets(3), 1 ticket × 2 events in same company: total=2 → blocked")
    void companyMinTickets_twoEvents_companyTotalBelowMin_checkoutBlocked() {
        String event2Id = "policy-test-ev-3";
        String zone2Id  = "policy-test-zone-3";

        ProductionCompany company = new ProductionCompany(COMPANY_ID, "Test Co", "Desc", USER_ID);
        company.setPurchasePolicy(new MinTicketsPolicy(3));
        companyRepo.save(company);

        seedEventWithId(EVENT_ID, ZONE_ID, COMPANY_ID, null, null);
        seedEventWithId(event2Id, zone2Id, COMPANY_ID, null, null);
        seedUser();

        String orderId = addTicketsForEvent(EVENT_ID, ZONE_ID, 1);
        addTicketsForEvent(event2Id, zone2Id, 1);

        Result<OrderHistoryDTO> result = checkout(orderId);
        assertFalse(result.isSuccess(),
                "company MinTickets(3) should block when only 1+1=2 tickets across two events");
    }

    // ── Discount: no discount ─────────────────────────────────────────────────────

    @Test
    @DisplayName("No discount on event or company: full price paid")
    void noDiscount_fullPricePaid() {
        seedCompany(null, null);
        seedEvent(null, null);
        seedUser();

        String orderId = addTickets(1);
        Result<OrderHistoryDTO> result = checkout(orderId);
        assertTrue(result.isSuccess());
        assertEquals(BASE_PRICE, result.getData().orElseThrow().totalPaid(), 0.01);
    }

    // ── Discount: event-level ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Event SimpleDiscount(10%), buy 1: 10% off base price")
    void eventSimpleDiscount_tenPercent_correctPriceCharged() {
        LocalDate today = LocalDate.now();
        seedCompany(null, null);
        seedEvent(null, new SimpleDiscount(0.10, today.minusDays(1), today.plusDays(1)));
        seedUser();

        String orderId = addTickets(1);
        Result<OrderHistoryDTO> result = checkout(orderId);
        assertTrue(result.isSuccess());
        assertEquals(BASE_PRICE * 0.90, result.getData().orElseThrow().totalPaid(), 0.01);
    }

    // ── Discount: company-level ───────────────────────────────────────────────────

    @Test
    @DisplayName("Company SimpleDiscount(20%), buy 1: 20% off base price")
    void companySimpleDiscount_twentyPercent_correctPriceCharged() {
        LocalDate today = LocalDate.now();
        seedCompany(null, new SimpleDiscount(0.20, today.minusDays(1), today.plusDays(1)));
        seedEvent(null, null);
        seedUser();

        String orderId = addTickets(1);
        Result<OrderHistoryDTO> result = checkout(orderId);
        assertTrue(result.isSuccess());
        assertEquals(BASE_PRICE * 0.80, result.getData().orElseThrow().totalPaid(), 0.01);
    }

    // ── Discount: additive combination ────────────────────────────────────────────

    @Test
    @DisplayName("Event 10% + Company 20% (additive): 30% off per ticket")
    void eventAndCompanyDiscount_additive_thirtyPercentOff() {
        LocalDate today = LocalDate.now();
        seedCompany(null, new SimpleDiscount(0.20, today.minusDays(1), today.plusDays(1)));
        seedEvent(null, new SimpleDiscount(0.10, today.minusDays(1), today.plusDays(1)));
        seedUser();

        String orderId = addTickets(1);
        Result<OrderHistoryDTO> result = checkout(orderId);
        assertTrue(result.isSuccess());
        assertEquals(BASE_PRICE * 0.70, result.getData().orElseThrow().totalPaid(), 0.01);
    }

    @Test
    @DisplayName("Event 10% + Company 20% (additive), buy 2 tickets: 30% off applies to both")
    void eventAndCompanyDiscount_additive_multipleTickets_discountAppliedPerTicket() {
        LocalDate today = LocalDate.now();
        seedCompany(null, new SimpleDiscount(0.20, today.minusDays(1), today.plusDays(1)));
        seedEvent(null, new SimpleDiscount(0.10, today.minusDays(1), today.plusDays(1)));
        seedUser();

        String orderId = addTickets(2);
        Result<OrderHistoryDTO> result = checkout(orderId);
        assertTrue(result.isSuccess());
        assertEquals(BASE_PRICE * 0.70 * 2, result.getData().orElseThrow().totalPaid(), 0.01);
    }

    // ── Discount: conditional ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Company ConditionalDiscount(30%, min3), buy 2: condition not met → full price")
    void companyConditionalDiscount_belowMinTickets_noDiscountApplied() {
        seedCompany(null, new ConditionalDiscount(0.30, 3));
        seedEvent(null, null);
        seedUser();

        String orderId = addTickets(2);
        Result<OrderHistoryDTO> result = checkout(orderId);
        assertTrue(result.isSuccess());
        assertEquals(BASE_PRICE * 2, result.getData().orElseThrow().totalPaid(), 0.01);
    }

    @Test
    @DisplayName("Company ConditionalDiscount(30%, min3), buy 3: condition met → 30% off each ticket")
    void companyConditionalDiscount_exactlyMinTickets_discountApplied() {
        seedCompany(null, new ConditionalDiscount(0.30, 3));
        seedEvent(null, null);
        seedUser();

        String orderId = addTickets(3);
        Result<OrderHistoryDTO> result = checkout(orderId);
        assertTrue(result.isSuccess());
        assertEquals(BASE_PRICE * 0.70 * 3, result.getData().orElseThrow().totalPaid(), 0.01);
    }

    @Test
    @DisplayName("Event ConditionalDiscount(20%, min2) + Company SimpleDiscount(10%), buy 2: both apply")
    void eventConditionalAndCompanySimple_conditionMet_bothDiscountsApplied() {
        LocalDate today = LocalDate.now();
        seedCompany(null, new SimpleDiscount(0.10, today.minusDays(1), today.plusDays(1)));
        seedEvent(null, new ConditionalDiscount(0.20, 2));
        seedUser();

        String orderId = addTickets(2);
        Result<OrderHistoryDTO> result = checkout(orderId);
        assertTrue(result.isSuccess());
        // 20% event + 10% company = 30% off per ticket × 2 tickets
        assertEquals(BASE_PRICE * 0.70 * 2, result.getData().orElseThrow().totalPaid(), 0.01);
    }

    @Test
    @DisplayName("Event ConditionalDiscount(20%, min2) + Company SimpleDiscount(10%), buy 1: event condition not met → only company discount")
    void eventConditionalAndCompanySimple_conditionNotMet_onlyCompanyDiscountApplied() {
        LocalDate today = LocalDate.now();
        seedCompany(null, new SimpleDiscount(0.10, today.minusDays(1), today.plusDays(1)));
        seedEvent(null, new ConditionalDiscount(0.20, 2));
        seedUser();

        String orderId = addTickets(1);
        Result<OrderHistoryDTO> result = checkout(orderId);
        assertTrue(result.isSuccess());
        // ConditionalDiscount not met (1 < 2) → only 10% company discount
        assertEquals(BASE_PRICE * 0.90, result.getData().orElseThrow().totalPaid(), 0.01);
    }

    // ── Inner test doubles ────────────────────────────────────────────────────────

    static class StubPaymentGateway implements IPaymentGateway {
        @Override public boolean        isConnected()                                { return true; }
        @Override public Result<String> processPayment(double amount, String details){ return Result.success("TXN-" + UUID.randomUUID()); }
        @Override public Result<Void>   refundPayment(String txId)                  { return Result.success(); }
        @Override public Result<Void>   refundPartial(String txId, double amount)   { return Result.success(); }
    }

    static class StubAuth implements IAuth {
        private final String userId;
        private final String validToken;

        StubAuth(String userId, String validToken) {
            this.userId     = userId;
            this.validToken = validToken;
        }

        @Override public String  generateToken(String uid)   { return validToken; }
        @Override public boolean validateToken(String token) { return validToken.equals(token); }
        @Override public String  extractUserId(String token) { return userId; }
    }

    static class StubTicketSupplier implements ITicketSupplier {
        @Override public boolean isConnected() { return true; }

        @Override
        public Result<List<String>> issueTickets(String customerId,
                java.util.List<com.sadna.group13a.application.Interfaces.TicketIssueRequest> requests) {
            List<String> codes = new java.util.ArrayList<>();
            for (int i = 0; i < requests.size(); i++) codes.add("TICKET-" + UUID.randomUUID());
            return Result.success(codes);
        }

        @Override
        public Result<Void> cancelTickets(List<String> ticketCodes) { return Result.success(); }
    }

    static class NoOpNotificationService implements INotificationService {
        @Override public void notifyOrderCompleted(String u, String r, double t) {}
        @Override public void notifyQueueTurnArrived(String u, String e, LocalDateTime t) {}
        @Override public void notifyUserBanned(String u, String adminId) {}
        @Override public void notifyUserSuspended(String u, LocalDateTime until) {}
        @Override public void notifyCompanyClosed(List<String> s, String c, String adminId) {}
        @Override public void notifyRaffleDrawn(List<String> p, String e, int w) {}
        @Override public void notifyActionFailed(String userId, String reason) {}
        @Override public void notifyCompanySuspended(List<String> s, String c) {}
        @Override public void notifyCompanyReopened(List<String> s, String c) {}
        @Override public void notifyStaffNominated(String u, String c, String role) {}
        @Override public void notifyStaffRemoved(String u, String c) {}
        @Override public void notifyPermissionsUpdated(String u, String c) {}
        @Override public void notifyCartExpired(String u) {}
        @Override public void notifyEventCancelled(List<String> b, String e, String title) {}
        @Override public void notifyRefundIssued(String u, String r, double a, String t) {}
        @Override public void notifyEventRescheduled(List<String> b, String e, String title, LocalDateTime d) {}
        @Override public void notifyUserReactivated(String u) {}
        @Override public void notifyEventSoldOut(List<String> s, String e, String title) {}
        @Override public void notifyRaffleWon(String u, String e, String code, LocalDateTime exp) {}
        @Override public void notifyAdminMessage(String u, String message) {}
    }
}
