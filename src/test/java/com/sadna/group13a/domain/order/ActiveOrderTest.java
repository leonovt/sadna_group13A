package com.sadna.group13a.domain.order;

import com.sadna.group13a.domain.shared.DomainException;
import com.sadna.group13a.domain.shared.UserType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ActiveOrder Aggregate Tests")
class ActiveOrderTest {

    private ActiveOrder order;

    @BeforeEach
    void setUp() {
        order = new ActiveOrder("order-1", "user-1", UserType.MEMBER);
    }

    @Test
    @DisplayName("Given valid params — When creating order — Then initialized correctly")
    void GivenValidParams_WhenCreating_ThenInitialized() {
        assertEquals("order-1", order.getOrderId());
        assertEquals("user-1", order.getUserId());
        assertEquals(UserType.MEMBER, order.getUserType());
        assertEquals(OrderStatus.DRAFT, order.getStatus());
        assertTrue(order.getItems().isEmpty());
        assertNotNull(order.getCreatedAt());
        assertNull(order.getExpiresAt());
    }

    @Test
    @DisplayName("Given valid item — When adding first item — Then timer initiates")
    void GivenValidItem_WhenAddingFirstItem_ThenTimerInitiates() {
        OrderItem item = new OrderItem("event-1", "zone-1", "seat-A1", 100.0);
        order.addItem(item);

        assertEquals(1, order.getItems().size());
        assertEquals(100.0, order.calculateTotalBasePrice());
        assertNotNull(order.getExpiresAt());
    }

    @Test
    @DisplayName("Given valid items — When calculating total — Then returns sum of base prices")
    void GivenItems_WhenCalculatingTotal_ThenReturnsSum() {
        order.addItem(new OrderItem("event-1", "zone-1", "seat-A1", 50.0));
        order.addItem(new OrderItem("event-1", "zone-1", "seat-A2", 75.5));

        assertEquals(125.5, order.calculateTotalBasePrice());
    }

    @Test
    @DisplayName("Given non-DRAFT order — When adding item — Then throws DomainException")
    void GivenNonDraftOrder_WhenAddingItem_ThenThrows() {
        order.addItem(new OrderItem("event-1", "zone-1", "seat-A1", 50.0));
        order.complete("txn-123");

        assertThrows(DomainException.class, 
            () -> order.addItem(new OrderItem("event-1", "zone-1", "seat-A2", 50.0)));
    }

    @Test
    @DisplayName("Given DRAFT order with items — When completing — Then status becomes COMPLETED")
    void GivenDraftOrderWithItems_WhenCompleting_ThenSuccess() {
        order.addItem(new OrderItem("event-1", "zone-1", "seat-A1", 50.0));
        order.complete("txn-123");

        assertEquals(OrderStatus.COMPLETED, order.getStatus());
        assertEquals("txn-123", order.getTransactionId());
    }

    @Test
    @DisplayName("Given empty DRAFT order — When completing — Then throws DomainException")
    void GivenEmptyDraftOrder_WhenCompleting_ThenThrows() {
        assertThrows(DomainException.class, () -> order.complete("txn-123"));
    }

    @Test
    @DisplayName("Given DRAFT order — When cancelling — Then status becomes CANCELLED")
    void GivenDraftOrder_WhenCancelling_ThenSuccess() {
        order.cancel();
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
    }
}
