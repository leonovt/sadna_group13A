package com.sadna.group13a.domain.DomainServices;

import com.sadna.group13a.domain.Aggregates.ActiveOrder.ActiveOrder;
import com.sadna.group13a.domain.Aggregates.ActiveOrder.OrderItem;
import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import com.sadna.group13a.domain.Aggregates.Event.Event;
import com.sadna.group13a.domain.Aggregates.Event.Seat;
import com.sadna.group13a.domain.Aggregates.Event.SeatStatus;
import com.sadna.group13a.domain.Aggregates.Event.SeatedZone;
import com.sadna.group13a.domain.Aggregates.Event.StandingZone;
import com.sadna.group13a.domain.Aggregates.Event.VenueMap;
import com.sadna.group13a.domain.Aggregates.OrderHistory.OrderHistoryItem;
import com.sadna.group13a.domain.policies.discount.AdditiveDiscountPolicy;
import com.sadna.group13a.domain.policies.discount.ConditionalDiscount;
import com.sadna.group13a.domain.policies.discount.NoDiscountPolicy;
import com.sadna.group13a.domain.policies.purchase.AgeRestrictionPolicy;
import com.sadna.group13a.domain.policies.purchase.AllowAllPolicy;
import com.sadna.group13a.domain.policies.purchase.AndPolicy;
import com.sadna.group13a.domain.policies.purchase.MinTicketsPolicy;
import com.sadna.group13a.domain.policies.purchase.OrPolicy;
import com.sadna.group13a.domain.shared.DiscountContext;
import com.sadna.group13a.domain.shared.DiscountPolicy;
import com.sadna.group13a.domain.shared.DomainException;
import com.sadna.group13a.domain.shared.PurchaseContext;
import com.sadna.group13a.domain.shared.PurchasePolicy;
import com.sadna.group13a.domain.shared.SeatUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;


class CheckoutDomainServiceTest {

    private static final String USER_ID       = "buyer-1";
    private static final String COMPANY_ID    = "company-1";
    private static final String EVENT_ID      = "event-1";
    private static final String SEATED_ZONE   = "zone-seated";
    private static final String STANDING_ZONE = "zone-standing";

    private CheckoutDomainService service;
    private ProductionCompany company;
    private Event event;
    private Seat seat;
    private SeatedZone seatedZone;
    private StandingZone standingZone;
    private ActiveOrder order;

    @BeforeEach
    void setUp() {
        service = new CheckoutDomainService();
        company = new ProductionCompany(COMPANY_ID, "Test Corp", "Events", "founder-1");

        seat = new Seat("seat-1", "A-1");
        seatedZone   = new SeatedZone(SEATED_ZONE,   "VIP",     100.0, List.of(seat));
        standingZone = new StandingZone(STANDING_ZONE, "General", 50.0, 10);

        VenueMap venueMap = new VenueMap("vm-1", "Arena");
        venueMap.addZone(seatedZone);
        venueMap.addZone(standingZone);

        event = new Event(EVENT_ID, "Rock Concert", "Desc", COMPANY_ID,
                LocalDateTime.now().plusDays(7), "Music");
        event.setVenueMap(venueMap);

        order = new ActiveOrder(UUID.randomUUID().toString(), USER_ID);
    }

    private List<OrderHistoryItem> checkout(List<OrderItem> items) {
        PurchaseContext pCtx = new PurchaseContext(USER_ID, items.size(), 0, null);
        DiscountContext dCtx = new DiscountContext(USER_ID, items.size(), null);
        return service.checkoutItemsForEvent(items, order, event, company,
                new AllowAllPolicy(), new NoDiscountPolicy(), pCtx, dCtx);
    }

    // ══════════════════════════════════════════════════════════════
    // SeatedZone checkout
    // ══════════════════════════════════════════════════════════════

    @Nested
    class SeatedZoneCheckoutTests {

        @Test
        void givenOrderWithHeldSeat_whenCheckout_thenSeatIsSoldAndReceiptIsBuilt() {
            seat.hold(USER_ID);
            order.addItem(new OrderItem(EVENT_ID, SEATED_ZONE, seat.getId(), 100.0));

            List<OrderHistoryItem> items = checkout(order.getItems());

            assertEquals(1, items.size());
            assertEquals(100.0, items.get(0).getPricePaid(), 0.001);
            assertEquals("A-1", items.get(0).getSeatLabel());
            assertEquals(SeatStatus.SOLD, seat.getStatus());
        }

        @Test
        void givenAvailableSeat_whenCheckout_thenThrowsSeatUnavailableException() {
            // seat was never held — sell() will reject it
            order.addItem(new OrderItem(EVENT_ID, SEATED_ZONE, seat.getId(), 100.0));

            assertThrows(SeatUnavailableException.class,
                    () -> checkout(order.getItems()));
        }

        @Test
        void givenItemWithNonExistentSeatId_whenCheckout_thenThrowsDomainException() {
            order.addItem(new OrderItem(EVENT_ID, SEATED_ZONE, "ghost-seat-id", 100.0));

            assertThrows(DomainException.class,
                    () -> checkout(order.getItems()));
        }

        @Test
        void givenSecondSeatFailsDuringCheckout_thenFirstSeatIsRolledBack() {
            // Two seated items: seat held + ghost seat that doesn't exist.
            // After failure the first seat must be back to AVAILABLE (rollback).
            Seat seat2 = new Seat("seat-2", "A-2");
            SeatedZone zone2 = new SeatedZone("zone-2", "VIP2", 80.0, List.of(seat2));
            event.getVenueMap().addZone(zone2);

            seat.hold(USER_ID); // seat 1 will succeed
            // seat2 is NOT held → sell() throws SeatUnavailableException

            List<OrderItem> items = List.of(
                    new OrderItem(EVENT_ID, SEATED_ZONE, seat.getId(), 100.0),
                    new OrderItem(EVENT_ID, "zone-2", seat2.getId(), 80.0)
            );

            PurchaseContext pCtx = new PurchaseContext(USER_ID, items.size(), 0, null);
            DiscountContext dCtx = new DiscountContext(USER_ID, items.size(), null);
            assertThrows(SeatUnavailableException.class,
                    () -> service.checkoutItemsForEvent(items, order, event, company,
                            new AllowAllPolicy(), new NoDiscountPolicy(), pCtx, dCtx));

            // seat 1 must have been rolled back to AVAILABLE
            assertEquals(SeatStatus.AVAILABLE, seat.getEffectiveStatus());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // StandingZone checkout
    // ══════════════════════════════════════════════════════════════

    @Nested
    class StandingZoneCheckoutTests {

        @Test
        void givenOrderWithHeldStandingSpot_whenCheckout_thenSeatLabelIsNullAndTotalIsBasePrice() {
            standingZone.holdStandingSpot(USER_ID);
            order.addItem(new OrderItem(EVENT_ID, STANDING_ZONE, null, 50.0));

            List<OrderHistoryItem> items = checkout(order.getItems());

            assertEquals(50.0, items.get(0).getPricePaid(), 0.001);
            assertNull(items.get(0).getSeatLabel());
        }

        @Test
        void givenStandingSpotWithoutHold_whenCheckout_thenThrowsSeatUnavailableException() {
            // no holdStandingSpot() called — sellStandingSpot() will reject it
            order.addItem(new OrderItem(EVENT_ID, STANDING_ZONE, null, 50.0));

            assertThrows(SeatUnavailableException.class,
                    () -> checkout(order.getItems()));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Multi-item total
    // ══════════════════════════════════════════════════════════════

    @Test
    void givenMultipleItems_whenCheckout_thenTotalPaidIsSumOfFinalPrices() {
        seat.hold(USER_ID);
        standingZone.holdStandingSpot(USER_ID);
        order.addItem(new OrderItem(EVENT_ID, SEATED_ZONE,   seat.getId(), 100.0));
        order.addItem(new OrderItem(EVENT_ID, STANDING_ZONE, null,          50.0));

        List<OrderHistoryItem> items = checkout(order.getItems());

        double total = items.stream().mapToDouble(OrderHistoryItem::getPricePaid).sum();
        assertEquals(150.0, total, 0.001);
        assertEquals(2, items.size());
    }

    // ══════════════════════════════════════════════════════════════
    // DiscountPolicy
    // ══════════════════════════════════════════════════════════════

    @Nested
    class DiscountPolicyTests {

        @Test
        void givenDiscountPolicy_whenCheckout_thenFinalPriceIsReduced() {
            seat.hold(USER_ID);
            order.addItem(new OrderItem(EVENT_ID, SEATED_ZONE, seat.getId(), 100.0));

            DiscountPolicy twentyPercentOff = (basePrice, ctx) -> basePrice * 0.20;
            PurchaseContext pCtx = new PurchaseContext(USER_ID, 1, 0, null);
            DiscountContext dCtx = new DiscountContext(USER_ID, 1, null);
            List<OrderHistoryItem> items = service.checkoutItemsForEvent(
                    order.getItems(), order, event, company,
                    new AllowAllPolicy(), twentyPercentOff, pCtx, dCtx);

            assertEquals(80.0, items.get(0).getPricePaid(), 0.001);
        }

        @Test
        void givenDiscountExceedingBasePrice_whenCheckout_thenFinalPriceClampedToZero() {
            seat.hold(USER_ID);
            order.addItem(new OrderItem(EVENT_ID, SEATED_ZONE, seat.getId(), 100.0));

            DiscountPolicy overkill = (basePrice, ctx) -> basePrice * 2;
            PurchaseContext pCtx = new PurchaseContext(USER_ID, 1, 0, null);
            DiscountContext dCtx = new DiscountContext(USER_ID, 1, null);
            List<OrderHistoryItem> items = service.checkoutItemsForEvent(
                    order.getItems(), order, event, company,
                    new AllowAllPolicy(), overkill, pCtx, dCtx);

            assertEquals(0.0, items.get(0).getPricePaid(), 0.001);
        }

        @Test
        void givenNullDiscountPolicyList_whenCheckout_thenNoDiscountApplied() {
            seat.hold(USER_ID);
            order.addItem(new OrderItem(EVENT_ID, SEATED_ZONE, seat.getId(), 100.0));

            List<OrderHistoryItem> items = checkout(order.getItems());

            assertEquals(100.0, items.get(0).getPricePaid(), 0.001);
        }

        @Test
        void givenMultipleDiscountPolicies_whenCheckout_thenDiscountsAreSummed() {
            seat.hold(USER_ID);
            order.addItem(new OrderItem(EVENT_ID, SEATED_ZONE, seat.getId(), 100.0));

            // 10% off + 5% off = 15 off → 85
            DiscountPolicy ten  = (base, ctx) -> base * 0.10;
            DiscountPolicy five = (base, ctx) -> base * 0.05;
            DiscountPolicy combined = new AdditiveDiscountPolicy(List.of(ten, five));
            PurchaseContext pCtx = new PurchaseContext(USER_ID, 1, 0, null);
            DiscountContext dCtx = new DiscountContext(USER_ID, 1, null);
            List<OrderHistoryItem> items = service.checkoutItemsForEvent(
                    order.getItems(), order, event, company,
                    new AllowAllPolicy(), combined, pCtx, dCtx);

            assertEquals(85.0, items.get(0).getPricePaid(), 0.001);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // PurchasePolicy
    // ══════════════════════════════════════════════════════════════

    @Nested
    class PurchasePolicyTests {

        @Test
        void givenPurchasePolicySatisfied_whenCheckout_thenSucceeds() {
            seat.hold(USER_ID);
            order.addItem(new OrderItem(EVENT_ID, SEATED_ZONE, seat.getId(), 100.0));

            PurchasePolicy allowed = ctx -> true;
            PurchaseContext pCtx = new PurchaseContext(USER_ID, 1, 0, null);
            DiscountContext dCtx = new DiscountContext(USER_ID, 1, null);
            assertDoesNotThrow(() -> service.checkoutItemsForEvent(
                    order.getItems(), order, event, company,
                    allowed, new NoDiscountPolicy(), pCtx, dCtx));
        }

        @Test
        void givenPurchasePolicyNotSatisfied_whenCheckout_thenThrowsDomainException() {
            seat.hold(USER_ID);
            order.addItem(new OrderItem(EVENT_ID, SEATED_ZONE, seat.getId(), 100.0));

            PurchasePolicy blocked = ctx -> false;
            PurchaseContext pCtx = new PurchaseContext(USER_ID, 1, 0, null);
            DiscountContext dCtx = new DiscountContext(USER_ID, 1, null);
            assertThrows(DomainException.class,
                    () -> service.checkoutItemsForEvent(
                            order.getItems(), order, event, company,
                            blocked, new NoDiscountPolicy(), pCtx, dCtx));
        }

        @Test
        void givenAllPoliciesSatisfied_whenCheckout_thenSucceeds() {
            seat.hold(USER_ID);
            order.addItem(new OrderItem(EVENT_ID, SEATED_ZONE, seat.getId(), 100.0));

            PurchasePolicy combined = new AndPolicy(ctx -> true, ctx -> true);
            PurchaseContext pCtx = new PurchaseContext(USER_ID, 1, 0, null);
            DiscountContext dCtx = new DiscountContext(USER_ID, 1, null);
            assertDoesNotThrow(() -> service.checkoutItemsForEvent(
                    order.getItems(), order, event, company,
                    combined, new NoDiscountPolicy(), pCtx, dCtx));
        }

        @Test
        void givenOnePolicyNotSatisfied_whenCheckout_thenThrowsDomainException() {
            seat.hold(USER_ID);
            order.addItem(new OrderItem(EVENT_ID, SEATED_ZONE, seat.getId(), 100.0));

            PurchasePolicy combined = new AndPolicy(ctx -> true, ctx -> false);
            PurchaseContext pCtx = new PurchaseContext(USER_ID, 1, 0, null);
            DiscountContext dCtx = new DiscountContext(USER_ID, 1, null);
            assertThrows(DomainException.class,
                    () -> service.checkoutItemsForEvent(
                            order.getItems(), order, event, company,
                            combined, new NoDiscountPolicy(), pCtx, dCtx));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Combined PurchasePolicy + DiscountPolicy
    // ══════════════════════════════════════════════════════════════

    @Nested
    class CombinedPolicyAndDiscountTests {

        @Test
        void givenAgeRestrictionBlocks_whenCheckout_thenDiscountNeverApplied() {
            seat.hold(USER_ID);
            order.addItem(new OrderItem(EVENT_ID, SEATED_ZONE, seat.getId(), 100.0));

            PurchasePolicy agePolicy    = new AgeRestrictionPolicy(21);
            DiscountPolicy bigDiscount  = new ConditionalDiscount(0.50, 1);
            PurchaseContext pCtx = new PurchaseContext(USER_ID, 1, 18, null); // user is 18 → blocked
            DiscountContext dCtx = new DiscountContext(USER_ID, 1, null);

            assertThrows(DomainException.class,
                    () -> service.checkoutItemsForEvent(order.getItems(), order, event, company,
                            agePolicy, bigDiscount, pCtx, dCtx));
        }

        @Test
        void givenAgeRestrictionSatisfied_andConditionalDiscount_thenPriceIsReduced() {
            seat.hold(USER_ID);
            order.addItem(new OrderItem(EVENT_ID, SEATED_ZONE, seat.getId(), 100.0));

            PurchasePolicy agePolicy   = new AgeRestrictionPolicy(18);
            DiscountPolicy twentyOff   = new ConditionalDiscount(0.20, 1);
            PurchaseContext pCtx = new PurchaseContext(USER_ID, 1, 21, null); // age 21 ≥ 18 → allowed
            DiscountContext dCtx = new DiscountContext(USER_ID, 1, null);

            List<OrderHistoryItem> items = service.checkoutItemsForEvent(
                    order.getItems(), order, event, company, agePolicy, twentyOff, pCtx, dCtx);

            assertEquals(80.0, items.get(0).getPricePaid(), 0.001);
        }

        @Test
        void givenMinTicketsPolicySatisfied_andConditionalDiscountThresholdMet_thenDiscountApplied() {
            // 2 tickets: seated (100) + standing (50)
            seat.hold(USER_ID);
            standingZone.holdStandingSpot(USER_ID);
            order.addItem(new OrderItem(EVENT_ID, SEATED_ZONE,   seat.getId(), 100.0));
            order.addItem(new OrderItem(EVENT_ID, STANDING_ZONE, null,          50.0));

            PurchasePolicy minTwo   = new MinTicketsPolicy(2);
            DiscountPolicy tenOff   = new ConditionalDiscount(0.10, 2); // 10% if 2+ tickets
            PurchaseContext pCtx = new PurchaseContext(USER_ID, 2, 25, null);
            DiscountContext dCtx = new DiscountContext(USER_ID, 2, null);

            List<OrderHistoryItem> items = service.checkoutItemsForEvent(
                    order.getItems(), order, event, company, minTwo, tenOff, pCtx, dCtx);

            assertEquals(2, items.size());
            assertEquals(90.0, items.get(0).getPricePaid(), 0.001); // 100 − 10%
            assertEquals(45.0, items.get(1).getPricePaid(), 0.001); // 50  − 10%
        }

        @Test
        void givenMinTicketsPolicyNotMet_whenCheckout_thenBlockedAndNoItemsSold() {
            seat.hold(USER_ID);
            order.addItem(new OrderItem(EVENT_ID, SEATED_ZONE, seat.getId(), 100.0));

            PurchasePolicy minTwo = new MinTicketsPolicy(2); // needs 2, only 1 in order
            DiscountPolicy anyDiscount = new ConditionalDiscount(0.20, 1);
            PurchaseContext pCtx = new PurchaseContext(USER_ID, 1, 25, null);
            DiscountContext dCtx = new DiscountContext(USER_ID, 1, null);

            assertThrows(DomainException.class,
                    () -> service.checkoutItemsForEvent(order.getItems(), order, event, company,
                            minTwo, anyDiscount, pCtx, dCtx));

            // seat must stay HELD (not SOLD), because the policy check fires before seat transitions
            assertEquals(SeatStatus.HELD, seat.getEffectiveStatus());
        }

        @Test
        void givenOrPolicy_oneRestrictiveBranchOnePermissive_andDiscount_thenSucceedsWithDiscount() {
            seat.hold(USER_ID);
            order.addItem(new OrderItem(EVENT_ID, SEATED_ZONE, seat.getId(), 100.0));

            // OR: AgeRestriction(99) blocks, AllowAll passes → overall allowed
            PurchasePolicy orPolicy  = new OrPolicy(new AgeRestrictionPolicy(99), new AllowAllPolicy());
            DiscountPolicy thirtyOff = new ConditionalDiscount(0.30, 1);
            PurchaseContext pCtx = new PurchaseContext(USER_ID, 1, 25, null);
            DiscountContext dCtx = new DiscountContext(USER_ID, 1, null);

            List<OrderHistoryItem> items = service.checkoutItemsForEvent(
                    order.getItems(), order, event, company, orPolicy, thirtyOff, pCtx, dCtx);

            assertEquals(70.0, items.get(0).getPricePaid(), 0.001);
        }

        @Test
        void givenAndPolicy_bothPass_andAdditiveDiscount_thenBothDiscountsSummed() {
            seat.hold(USER_ID);
            order.addItem(new OrderItem(EVENT_ID, SEATED_ZONE, seat.getId(), 100.0));

            // AND(AllowAll, MinTickets1): both pass
            PurchasePolicy andPolicy = new AndPolicy(new AllowAllPolicy(), new MinTicketsPolicy(1));
            // Additive: 10% + 5% = 15% off
            DiscountPolicy additive  = new AdditiveDiscountPolicy(List.of(
                    new ConditionalDiscount(0.10, 1),
                    new ConditionalDiscount(0.05, 1)));
            PurchaseContext pCtx = new PurchaseContext(USER_ID, 1, 25, null);
            DiscountContext dCtx = new DiscountContext(USER_ID, 1, null);

            List<OrderHistoryItem> items = service.checkoutItemsForEvent(
                    order.getItems(), order, event, company, andPolicy, additive, pCtx, dCtx);

            assertEquals(85.0, items.get(0).getPricePaid(), 0.001);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Policy combination helpers
    // ══════════════════════════════════════════════════════════════

    @Nested
    class PolicyCombinationHelperTests {

        @Test
        void givenCombinePolicies_bothSatisfied_thenCheckoutSucceeds() {
            seat.hold(USER_ID);
            order.addItem(new OrderItem(EVENT_ID, SEATED_ZONE, seat.getId(), 100.0));

            PurchasePolicy combined = service.combinePolicies(new AllowAllPolicy(), new MinTicketsPolicy(1));
            PurchaseContext pCtx = new PurchaseContext(USER_ID, 1, 25, null);
            DiscountContext dCtx = new DiscountContext(USER_ID, 1, null);

            assertDoesNotThrow(() -> service.checkoutItemsForEvent(
                    order.getItems(), order, event, company,
                    combined, new NoDiscountPolicy(), pCtx, dCtx));
        }

        @Test
        void givenCombinePolicies_companyPolicyBlocks_thenCheckoutThrows() {
            seat.hold(USER_ID);
            order.addItem(new OrderItem(EVENT_ID, SEATED_ZONE, seat.getId(), 100.0));

            // Company requires age 99 — effectively always blocks
            PurchasePolicy combined = service.combinePolicies(new AllowAllPolicy(), new AgeRestrictionPolicy(99));
            PurchaseContext pCtx = new PurchaseContext(USER_ID, 1, 25, null);
            DiscountContext dCtx = new DiscountContext(USER_ID, 1, null);

            assertThrows(DomainException.class,
                    () -> service.checkoutItemsForEvent(order.getItems(), order, event, company,
                            combined, new NoDiscountPolicy(), pCtx, dCtx));
        }

        @Test
        void givenCombineDiscounts_eventAndCompanyDiscountsAreAdditive() {
            seat.hold(USER_ID);
            order.addItem(new OrderItem(EVENT_ID, SEATED_ZONE, seat.getId(), 100.0));

            DiscountPolicy eventDiscount   = new ConditionalDiscount(0.10, 1); // 10% off
            DiscountPolicy companyDiscount = new ConditionalDiscount(0.05, 1); // 5% off
            DiscountPolicy combined        = service.combineDiscounts(eventDiscount, companyDiscount);

            PurchaseContext pCtx = new PurchaseContext(USER_ID, 1, 25, null);
            DiscountContext dCtx = new DiscountContext(USER_ID, 1, null);

            List<OrderHistoryItem> items = service.checkoutItemsForEvent(
                    order.getItems(), order, event, company,
                    new AllowAllPolicy(), combined, pCtx, dCtx);

            assertEquals(85.0, items.get(0).getPricePaid(), 0.001); // 100 − 10% − 5%
        }

        @Test
        void givenCombineDiscounts_oneIsNoDiscount_thenOnlyOtherApplies() {
            seat.hold(USER_ID);
            order.addItem(new OrderItem(EVENT_ID, SEATED_ZONE, seat.getId(), 100.0));

            DiscountPolicy combined = service.combineDiscounts(
                    new NoDiscountPolicy(),
                    new ConditionalDiscount(0.20, 1));

            PurchaseContext pCtx = new PurchaseContext(USER_ID, 1, 25, null);
            DiscountContext dCtx = new DiscountContext(USER_ID, 1, null);

            List<OrderHistoryItem> items = service.checkoutItemsForEvent(
                    order.getItems(), order, event, company,
                    new AllowAllPolicy(), combined, pCtx, dCtx);

            assertEquals(80.0, items.get(0).getPricePaid(), 0.001);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Expired order guard
    // ══════════════════════════════════════════════════════════════

    @Test
    void givenExpiredOrder_whenCheckout_thenThrowsDomainException() throws Exception {
        Field expiresAtField = ActiveOrder.class.getDeclaredField("expiresAt");
        expiresAtField.setAccessible(true);
        expiresAtField.set(order, LocalDateTime.now().minusHours(1));

        assertThrows(DomainException.class,
                () -> checkout(order.getItems()));
    }
}
