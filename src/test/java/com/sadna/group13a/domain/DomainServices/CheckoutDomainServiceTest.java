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
import com.sadna.group13a.domain.shared.DiscountPolicy;
import com.sadna.group13a.domain.shared.DomainException;
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

    // helper to invoke the service with empty policy lists
    private List<OrderHistoryItem> checkout(List<OrderItem> items) {
        return service.checkoutItemsForEvent(items, order, event, company, List.of(), List.of());
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

            assertThrows(SeatUnavailableException.class,
                    () -> service.checkoutItemsForEvent(items, order, event, company, List.of(), List.of()));

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

            DiscountPolicy twentyPercentOff = basePrice -> basePrice * 0.20;
            List<OrderHistoryItem> items = service.checkoutItemsForEvent(
                    order.getItems(), order, event, company, List.of(), List.of(twentyPercentOff));

            assertEquals(80.0, items.get(0).getPricePaid(), 0.001);
        }

        @Test
        void givenDiscountExceedingBasePrice_whenCheckout_thenFinalPriceClampedToZero() {
            seat.hold(USER_ID);
            order.addItem(new OrderItem(EVENT_ID, SEATED_ZONE, seat.getId(), 100.0));

            DiscountPolicy overkill = basePrice -> basePrice * 2;
            List<OrderHistoryItem> items = service.checkoutItemsForEvent(
                    order.getItems(), order, event, company, List.of(), List.of(overkill));

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

            // 10% off from event policy + 5% off from company policy = 15 off → 85
            DiscountPolicy ten  = base -> base * 0.10;
            DiscountPolicy five = base -> base * 0.05;
            List<OrderHistoryItem> items = service.checkoutItemsForEvent(
                    order.getItems(), order, event, company, List.of(), List.of(ten, five));

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

            PurchasePolicy allowed = () -> true;
            assertDoesNotThrow(() -> service.checkoutItemsForEvent(
                    order.getItems(), order, event, company, List.of(allowed), List.of()));
        }

        @Test
        void givenPurchasePolicyNotSatisfied_whenCheckout_thenThrowsDomainException() {
            seat.hold(USER_ID);
            order.addItem(new OrderItem(EVENT_ID, SEATED_ZONE, seat.getId(), 100.0));

            PurchasePolicy blocked = () -> false;
            assertThrows(DomainException.class,
                    () -> service.checkoutItemsForEvent(
                            order.getItems(), order, event, company, List.of(blocked), List.of()));
        }

        @Test
        void givenAllPoliciesSatisfied_whenCheckout_thenSucceeds() {
            seat.hold(USER_ID);
            order.addItem(new OrderItem(EVENT_ID, SEATED_ZONE, seat.getId(), 100.0));

            assertDoesNotThrow(() -> service.checkoutItemsForEvent(
                    order.getItems(), order, event, company,
                    List.of(() -> true, () -> true), List.of()));
        }

        @Test
        void givenOnePolicyNotSatisfied_whenCheckout_thenThrowsDomainException() {
            seat.hold(USER_ID);
            order.addItem(new OrderItem(EVENT_ID, SEATED_ZONE, seat.getId(), 100.0));

            assertThrows(DomainException.class,
                    () -> service.checkoutItemsForEvent(
                            order.getItems(), order, event, company,
                            List.of(() -> true, () -> false), List.of()));
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
