package com.sadna.group13a.domain.Aggregates.ActiveOrder;

import com.sadna.group13a.domain.shared.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;


class ActiveOrderTest {

    private static final String USER_ID = "user-abc";
    private static final String EVENT_ID = "event-1";
    private static final String ZONE_ID = "zone-1";

    private ActiveOrder order;

    @BeforeEach
    void setUp() {
        order = new ActiveOrder(UUID.randomUUID().toString(), USER_ID);
    }

    // ── Construction ──────────────────────────────────────────────

    @Test
    void givenValidParams_whenCreatingOrder_thenStatusIsOpenAndItemsAreEmpty() {
        assertEquals(OrderStatus.OPEN, order.getStatus());
        assertTrue(order.getItems().isEmpty());
        assertEquals(USER_ID, order.getUserId());
        assertFalse(order.isExpired());
    }

    @Test
    void givenNewOrder_whenCheckingExpiry_thenWindowMatchesSeatHoldDuration() {
        // Issue #191: the cart's bounded window must equal the seat-hold window so the cart
        // is never valid after its held seats are released. Single source of truth = Seat.
        assertEquals(order.getCreatedAt().plus(com.sadna.group13a.domain.Aggregates.Event.Seat.DEFAULT_HOLD_DURATION),
                order.getExpiresAt(),
                "Cart expiry must equal createdAt + Seat.DEFAULT_HOLD_DURATION");
    }

    @Test
    void givenBlankId_whenCreatingOrder_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new ActiveOrder("  ", USER_ID));
    }

    @Test
    void givenBlankUserId_whenCreatingOrder_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ActiveOrder(UUID.randomUUID().toString(), ""));
    }

    // ── addItem: positive ─────────────────────────────────────────

    @Test
    void givenOpenOrder_whenItemAdded_thenItemsContainsIt() {
        OrderItem item = new OrderItem(EVENT_ID, ZONE_ID, "seat-1", 100.0);
        order.addItem(item);

        assertEquals(1, order.getItems().size());
        assertEquals(item, order.getItems().get(0));
    }

    @Test
    void givenOpenOrder_whenMultipleItemsAdded_thenTotalPriceIsSum() {
        order.addItem(new OrderItem(EVENT_ID, ZONE_ID, "seat-1", 100.0));
        order.addItem(new OrderItem(EVENT_ID, ZONE_ID, "seat-2", 150.0));

        assertEquals(250.0, order.getTotalPrice(), 0.001);
    }

    // ── addItem: negative ─────────────────────────────────────────

    @Test
    void givenOpenOrder_whenNullItemAdded_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> order.addItem(null));
    }

    @Test
    void givenSubmittedOrder_whenAddingItem_thenThrowsIllegalStateException() {
        order.addItem(new OrderItem(EVENT_ID, ZONE_ID, "seat-1", 100.0));
        order.submit();

        assertThrows(IllegalStateException.class,
                () -> order.addItem(new OrderItem(EVENT_ID, ZONE_ID, "seat-2", 100.0)));
    }

    // ── removeItem ────────────────────────────────────────────────

    @Test
    void givenOpenOrderWithItem_whenItemRemoved_thenItemsIsEmpty() {
        OrderItem item = new OrderItem(EVENT_ID, ZONE_ID, "seat-1", 100.0);
        order.addItem(item);
        order.removeItem(item);

        assertTrue(order.getItems().isEmpty());
    }

    @Test
    void givenCancelledOrder_whenRemovingItem_thenThrowsIllegalStateException() {
        OrderItem item = new OrderItem(EVENT_ID, ZONE_ID, "seat-1", 100.0);
        order.addItem(item);
        order.cancel();

        assertThrows(IllegalStateException.class, () -> order.removeItem(item));
    }

    // ── submit ────────────────────────────────────────────────────

    @Test
    void givenOpenOrderWithItems_whenSubmitted_thenStatusIsPendingPayment() {
        order.addItem(new OrderItem(EVENT_ID, ZONE_ID, "seat-1", 100.0));
        order.submit();

        assertEquals(OrderStatus.PENDING_PAYMENT, order.getStatus());
    }

    @Test
    void givenOpenOrderWithNoItems_whenSubmitted_thenThrowsIllegalStateException() {
        assertThrows(IllegalStateException.class, order::submit);
    }

    @Test
    void givenSubmittedOrder_whenSubmittedAgain_thenThrowsIllegalStateException() {
        order.addItem(new OrderItem(EVENT_ID, ZONE_ID, "seat-1", 100.0));
        order.submit();

        assertThrows(IllegalStateException.class, order::submit);
    }

    // ── cancel ────────────────────────────────────────────────────

    @Test
    void givenOpenOrder_whenCancelled_thenStatusIsCancelled() {
        order.cancel();

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
    }

    @Test
    void givenSubmittedOrder_whenCancelled_thenStatusIsCancelled() {
        order.addItem(new OrderItem(EVENT_ID, ZONE_ID, "seat-1", 100.0));
        order.submit();
        order.cancel();

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
    }


    // ── Immutability of items list ────────────────────────────────

    @Test
    void givenOrder_whenModifyingReturnedItemsList_thenThrowsUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class,
                () -> order.getItems().add(new OrderItem(EVENT_ID, ZONE_ID, "seat-x", 100.0)));
    }

    // ── OrderItem value object ────────────────────────────────────

    @Test
    void givenTwoIdenticalOrderItems_thenTheyAreEqual() {
        OrderItem a = new OrderItem(EVENT_ID, ZONE_ID, "seat-1", 99.0);
        OrderItem b = new OrderItem(EVENT_ID, ZONE_ID, "seat-1", 99.0);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void givenOrderItemWithDifferentSeatId_thenNotEqual() {
        OrderItem a = new OrderItem(EVENT_ID, ZONE_ID, "seat-1", 99.0);
        OrderItem b = new OrderItem(EVENT_ID, ZONE_ID, "seat-2", 99.0);

        assertNotEquals(a, b);
    }

    @Test
    void givenNegativeBasePrice_whenCreatingOrderItem_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new OrderItem(EVENT_ID, ZONE_ID, "seat-1", -1.0));
    }

    @Test
    void givenBlankEventId_whenCreatingOrderItem_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new OrderItem("", ZONE_ID, "seat-1", 50.0));
    }
}
