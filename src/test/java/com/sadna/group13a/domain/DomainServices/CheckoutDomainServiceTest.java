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
import com.sadna.group13a.domain.Aggregates.OrderHistory.OrderHistory;
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

/**
 * Unit tests for the CheckoutDomainService.
 * No Spring, no Mockito — pure domain instantiation.
 */
class CheckoutDomainServiceTest {

    private static final String USER_ID      = "buyer-1";
    private static final String COMPANY_ID   = "company-1";
    private static final String EVENT_ID     = "event-1";
    private static final String SEATED_ZONE  = "zone-seated";
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
        seatedZone  = new SeatedZone(SEATED_ZONE,  "VIP",     100.0, List.of(seat));
        standingZone = new StandingZone(STANDING_ZONE, "General", 50.0, 10);

        VenueMap venueMap = new VenueMap("vm-1", "Arena");
        venueMap.addZone(seatedZone);
        venueMap.addZone(standingZone);

        event = new Event(EVENT_ID, "Rock Concert", "Desc", COMPANY_ID,
                LocalDateTime.now().plusDays(7), "Music");
        event.setVenueMap(venueMap);

        order = new ActiveOrder(UUID.randomUUID().toString(), USER_ID);
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

            OrderHistory history = service.checkout(order, event, company, null, null);

            assertEquals(USER_ID, history.getUserId());
            assertEquals(1, history.getItems().size());
            assertEquals(100.0, history.getTotalPaid(), 0.001);
            assertEquals("A-1", history.getItems().get(0).getSeatLabel());
            assertEquals(SeatStatus.SOLD, seat.getStatus());
        }

        @Test
        void givenAvailableSeat_whenCheckout_thenThrowsSeatUnavailableException() {
            // seat was never held — sell() will reject it
            order.addItem(new OrderItem(EVENT_ID, SEATED_ZONE, seat.getId(), 100.0));

            assertThrows(SeatUnavailableException.class,
                    () -> service.checkout(order, event, company, null, null));
        }

        @Test
        void givenItemWithNonExistentSeatId_whenCheckout_thenThrowsDomainException() {
            order.addItem(new OrderItem(EVENT_ID, SEATED_ZONE, "ghost-seat-id", 100.0));

            assertThrows(DomainException.class,
                    () -> service.checkout(order, event, company, null, null));
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

            OrderHistory history = service.checkout(order, event, company, null, null);

            assertEquals(50.0, history.getTotalPaid(), 0.001);
            assertNull(history.getItems().get(0).getSeatLabel());
        }

        @Test
        void givenStandingSpotWithoutHold_whenCheckout_thenThrowsSeatUnavailableException() {
            // no holdStandingSpot() called — sellStandingSpot() will reject it
            order.addItem(new OrderItem(EVENT_ID, STANDING_ZONE, null, 50.0));

            assertThrows(SeatUnavailableException.class,
                    () -> service.checkout(order, event, company, null, null));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Multi-item total
    // ══════════════════════════════════════════════════════════════

    @Test
    void givenMultipleItems_whenCheckout_thenTotalPaidIsSumOfFinalPrices() {
        seat.hold(USER_ID);
        standingZone.holdStandingSpot(USER_ID);
        order.addItem(new OrderItem(EVENT_ID, SEATED_ZONE,  seat.getId(), 100.0));
        order.addItem(new OrderItem(EVENT_ID, STANDING_ZONE, null,        50.0));

        OrderHistory history = service.checkout(order, event, company, null, null);

        assertEquals(150.0, history.getTotalPaid(), 0.001);
        assertEquals(2, history.getItems().size());
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
            OrderHistory history = service.checkout(order, event, company, twentyPercentOff, null);

            assertEquals(80.0, history.getTotalPaid(), 0.001);
        }

        @Test
        void givenDiscountExceedingBasePrice_whenCheckout_thenFinalPriceClampedToZero() {
            seat.hold(USER_ID);
            order.addItem(new OrderItem(EVENT_ID, SEATED_ZONE, seat.getId(), 100.0));

            DiscountPolicy overkill = basePrice -> basePrice * 2;
            OrderHistory history = service.checkout(order, event, company, overkill, null);

            assertEquals(0.0, history.getTotalPaid(), 0.001);
        }

        @Test
        void givenNullDiscountPolicy_whenCheckout_thenNoDiscountApplied() {
            seat.hold(USER_ID);
            order.addItem(new OrderItem(EVENT_ID, SEATED_ZONE, seat.getId(), 100.0));

            OrderHistory history = service.checkout(order, event, company, null, null);

            assertEquals(100.0, history.getTotalPaid(), 0.001);
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

            assertDoesNotThrow(() -> service.checkout(order, event, company, null, allowed));
        }

        @Test
        void givenPurchasePolicyNotSatisfied_whenCheckout_thenThrowsDomainException() {
            seat.hold(USER_ID);
            order.addItem(new OrderItem(EVENT_ID, SEATED_ZONE, seat.getId(), 100.0));

            PurchasePolicy blocked = () -> false;

            assertThrows(DomainException.class,
                    () -> service.checkout(order, event, company, null, blocked));
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
                () -> service.checkout(order, event, company, null, null));
    }
}
