package com.sadna.group13a.domain.Aggregates.OrderHistory;

import com.sadna.group13a.domain.Aggregates.User.UserRole;
import com.sadna.group13a.domain.shared.DomainException;
import com.sadna.group13a.domain.shared.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the OrderHistory-domain ActiveOrder aggregate and
 * its OrderItem value object.
 */
class OrderHistoryActiveOrderTest {

    private static final String ORDER_ID = UUID.randomUUID().toString();
    private static final String USER_ID   = "user-1";
    private static final UserRole ROLE    = UserRole.MEMBER;

    // ── Helpers ───────────────────────────────────────────────────────────────

    private com.sadna.group13a.domain.Aggregates.OrderHistory.ActiveOrder newOrder() {
        return new com.sadna.group13a.domain.Aggregates.OrderHistory.ActiveOrder(ORDER_ID, USER_ID, ROLE);
    }

    private OrderItem item(String seatId) {
        return new OrderItem("event-1", "zone-1", seatId, 50.0);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Construction
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class Construction {

        @Test
        void givenValidParams_thenFieldsAreStoredAndStatusIsOpen() {
            var order = newOrder();
            assertEquals(ORDER_ID, order.getOrderId());
            assertEquals(USER_ID, order.getUserId());
            assertEquals(ROLE, order.getUserRole());
            assertEquals(OrderStatus.OPEN, order.getStatus());
            assertTrue(order.getItems().isEmpty());
            assertNull(order.getExpiresAt(), "Timer must not start until first item is added");
            assertNull(order.getTransactionId());
            assertNotNull(order.getCreatedAt());
        }

        @Test
        void givenBlankOrderId_thenThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new com.sadna.group13a.domain.Aggregates.OrderHistory.ActiveOrder("  ", USER_ID, ROLE));
        }

        @Test
        void givenNullOrderId_thenThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new com.sadna.group13a.domain.Aggregates.OrderHistory.ActiveOrder(null, USER_ID, ROLE));
        }

        @Test
        void givenBlankUserId_thenThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new com.sadna.group13a.domain.Aggregates.OrderHistory.ActiveOrder(ORDER_ID, "", ROLE));
        }

        @Test
        void givenNullUserId_thenThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new com.sadna.group13a.domain.Aggregates.OrderHistory.ActiveOrder(ORDER_ID, null, ROLE));
        }

        @Test
        void givenNullUserRole_thenThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new com.sadna.group13a.domain.Aggregates.OrderHistory.ActiveOrder(ORDER_ID, USER_ID, null));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // isExpired
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class IsExpired {

        @Test
        void givenNoItemsAdded_thenNotExpired() {
            // expiresAt is null until first item is added
            assertFalse(newOrder().isExpired());
        }

        @Test
        void givenItemJustAdded_thenNotExpired() {
            var order = newOrder();
            order.addItem(item("seat-1"));
            assertFalse(order.isExpired());
        }

        @Test
        void givenCancelledOrder_thenNeverExpired() {
            var order = newOrder();
            order.cancel();
            assertFalse(order.isExpired(), "Cancelled order should not report as expired");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // addItem
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class AddItem {

        @Test
        void givenOpenOrder_whenFirstItemAdded_thenTimerStarts() {
            var order = newOrder();
            assertNull(order.getExpiresAt(), "Pre: no timer before first item");
            order.addItem(item("s1"));
            assertNotNull(order.getExpiresAt(), "Post: timer must start after first item");
            assertEquals(1, order.getItems().size());
        }

        @Test
        void givenOpenOrder_whenSecondItemAdded_thenTimerIsNotReset() {
            var order = newOrder();
            order.addItem(item("s1"));
            var firstExpiry = order.getExpiresAt();
            order.addItem(item("s2"));
            assertEquals(firstExpiry, order.getExpiresAt(), "Timer must not reset for subsequent items");
            assertEquals(2, order.getItems().size());
        }

        @Test
        void givenNullItem_thenThrowsIllegalArgument() {
            assertThrows(IllegalArgumentException.class, () -> newOrder().addItem(null));
        }

        @Test
        void givenCancelledOrder_whenAddingItem_thenThrowsDomainException() {
            var order = newOrder();
            order.cancel();
            assertThrows(DomainException.class, () -> order.addItem(item("s1")));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // removeItem
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class RemoveItem {

        @Test
        void givenOpenOrderWithItem_whenRemoved_thenItemsIsEmpty() {
            var order = newOrder();
            var it = item("seat-1");
            order.addItem(it);
            order.removeItem(it);
            assertTrue(order.getItems().isEmpty());
        }

        @Test
        void givenCancelledOrder_whenRemoving_thenThrowsDomainException() {
            var order = newOrder();
            order.addItem(item("seat-1"));
            order.cancel();
            assertThrows(DomainException.class, () -> order.removeItem(item("seat-1")));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // calculateTotalBasePrice
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class CalculateTotalBasePrice {

        @Test
        void givenEmptyOrder_thenZero() {
            assertEquals(0.0, newOrder().calculateTotalBasePrice(), 0.001);
        }

        @Test
        void givenTwoItems_thenSumIsReturned() {
            var order = newOrder();
            order.addItem(new OrderItem("event-1", "zone-1", "s1", 40.0));
            order.addItem(new OrderItem("event-1", "zone-1", "s2", 60.0));
            assertEquals(100.0, order.calculateTotalBasePrice(), 0.001);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // complete
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class Complete {

        @Test
        void givenOpenOrderWithItem_whenCompleted_thenStatusIsCompletedAndTransactionStored() {
            var order = newOrder();
            order.addItem(item("seat-1"));
            order.complete("txn-123");
            assertEquals(OrderStatus.COMPLETED, order.getStatus());
            assertEquals("txn-123", order.getTransactionId());
        }

        @Test
        void givenOpenEmptyOrder_whenCompleted_thenThrowsDomainException() {
            assertThrows(DomainException.class, () -> newOrder().complete("txn"));
        }

        @Test
        void givenCancelledOrder_whenCompleted_thenThrowsDomainException() {
            var order = newOrder();
            order.cancel();
            assertThrows(DomainException.class, () -> order.complete("txn"));
        }

        @Test
        void givenNullTransactionId_whenCompleting_thenThrowsIllegalArgument() {
            var order = newOrder();
            order.addItem(item("s1"));
            assertThrows(IllegalArgumentException.class, () -> order.complete(null));
        }

        @Test
        void givenBlankTransactionId_whenCompleting_thenThrowsIllegalArgument() {
            var order = newOrder();
            order.addItem(item("s1"));
            assertThrows(IllegalArgumentException.class, () -> order.complete("  "));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // cancel
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class Cancel {

        @Test
        void givenOpenOrder_whenCancelled_thenStatusIsCancelled() {
            var order = newOrder();
            order.cancel();
            assertEquals(OrderStatus.CANCELLED, order.getStatus());
        }

        @Test
        void givenCancelledOrder_whenCancelledAgain_thenThrowsDomainException() {
            var order = newOrder();
            order.cancel();
            assertThrows(DomainException.class, order::cancel);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // OrderItem value object
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class OrderItemTests {

        @Test
        void givenValidParams_thenFieldsStored() {
            var it = new OrderItem("evt", "zn", "seat", 99.0);
            assertEquals("evt", it.getEventId());
            assertEquals("zn", it.getZoneId());
            assertEquals("seat", it.getSeatId());
            assertEquals(99.0, it.getBasePrice(), 0.001);
        }

        @Test
        void givenNullSeatId_thenAllowed() {
            var it = new OrderItem("evt", "zn", null, 10.0);
            assertNull(it.getSeatId());
        }

        @Test
        void givenBlankEventId_thenThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new OrderItem("", "zone", "seat", 10.0));
        }

        @Test
        void givenBlankZoneId_thenThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new OrderItem("event", "", "seat", 10.0));
        }

        @Test
        void givenNegativeBasePrice_thenThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new OrderItem("event", "zone", "seat", -1.0));
        }

        @Test
        void givenTwoIdenticalItems_thenEqualAndSameHashCode() {
            var a = new OrderItem("evt", "zn", "s", 50.0);
            var b = new OrderItem("evt", "zn", "s", 50.0);
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        void givenSameReference_thenEquals() {
            var a = new OrderItem("evt", "zn", "s", 50.0);
            assertEquals(a, a);
        }

        @Test
        void givenDifferentSeat_thenNotEqual() {
            var a = new OrderItem("evt", "zn", "s1", 50.0);
            var b = new OrderItem("evt", "zn", "s2", 50.0);
            assertNotEquals(a, b);
        }

        @Test
        void givenNullComparison_thenNotEqual() {
            assertNotEquals(new OrderItem("evt", "zn", "s", 50.0), null);
        }

        @Test
        void givenBothNullSeatIds_thenEqual() {
            var a = new OrderItem("evt", "zn", null, 50.0);
            var b = new OrderItem("evt", "zn", null, 50.0);
            assertEquals(a, b);
        }
    }
}
