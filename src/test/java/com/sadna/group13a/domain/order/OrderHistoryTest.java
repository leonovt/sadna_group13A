package com.sadna.group13a.domain.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OrderHistory Aggregate Tests")
class OrderHistoryTest {

    @Test
    @DisplayName("Given valid parameters — When creating OrderHistory — Then fields are immutable and correctly set")
    void GivenValidParams_WhenCreating_ThenInitialized() {
        LocalDateTime now = LocalDateTime.now();
        OrderHistoryItem item = new OrderHistoryItem(
                "event-1", "Rock Concert", now.plusDays(5),
                "company-1", "Live Nation", "VIP Standing",
                null, 150.0);

        OrderHistory history = new OrderHistory("receipt-123", "user-1", now, 150.0, List.of(item));

        assertEquals("receipt-123", history.getReceiptId());
        assertEquals("user-1", history.getUserId());
        assertEquals(now, history.getPurchaseDate());
        assertEquals(150.0, history.getTotalPaid());
        assertEquals(1, history.getItems().size());
        
        OrderHistoryItem retrievedItem = history.getItems().get(0);
        assertEquals("event-1", retrievedItem.getEventId());
        assertEquals("Rock Concert", retrievedItem.getEventTitle());
        assertEquals("company-1", retrievedItem.getCompanyId());
        assertEquals("VIP Standing", retrievedItem.getZoneName());
        assertNull(retrievedItem.getSeatLabel());
        assertEquals(150.0, retrievedItem.getPricePaid());
    }

    @Test
    @DisplayName("Given invalid parameters — When creating OrderHistoryItem — Then throws IllegalArgumentException")
    void GivenInvalidParams_WhenCreatingItem_ThenThrows() {
        assertThrows(IllegalArgumentException.class, 
            () -> new OrderHistoryItem("", "Title", LocalDateTime.now(), "c1", "cName", "z1", null, 100));
        assertThrows(IllegalArgumentException.class, 
            () -> new OrderHistoryItem("e1", "Title", LocalDateTime.now(), "c1", "cName", "z1", null, -50));
    }

    @Test
    @DisplayName("Given invalid parameters — When creating OrderHistory — Then throws IllegalArgumentException")
    void GivenInvalidParams_WhenCreatingHistory_ThenThrows() {
        assertThrows(IllegalArgumentException.class, 
            () -> new OrderHistory("", "user-1", LocalDateTime.now(), 100, List.of()));
        assertThrows(IllegalArgumentException.class, 
            () -> new OrderHistory("r1", "user-1", LocalDateTime.now(), 100, null));
    }

    @Test
    @DisplayName("Given valid OrderHistory — When attempting to modify items list — Then throws UnsupportedOperationException")
    void GivenValidHistory_WhenModifyingItems_ThenThrows() {
        OrderHistoryItem item = new OrderHistoryItem(
                "e1", "Title", LocalDateTime.now(), "c1", "cName", "z1", null, 100);
        OrderHistory history = new OrderHistory("r1", "user-1", LocalDateTime.now(), 100, List.of(item));

        assertThrows(UnsupportedOperationException.class, () -> history.getItems().clear());
    }
}
