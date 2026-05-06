package com.sadna.group13a.domain.Aggregates.OrderHistory;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;


class OrderHistoryTest {

    private static final String COMPANY_ID = "company-1";

    // ── Helper ────────────────────────────────────────────────────

    private OrderHistoryItem buildItem(String companyId) {
        return new OrderHistoryItem(
                "event-1", "Rock Concert", LocalDateTime.now().plusDays(10),
                companyId, "Best Events Co.", "VIP", "A-1", 150.0);
    }

    private OrderHistory buildHistory(String userId, double total, List<OrderHistoryItem> items) {
        return new OrderHistory(UUID.randomUUID().toString(), userId, LocalDateTime.now(), total, items);
    }

    // ── Construction: positive ────────────────────────────────────

    @Test
    void givenValidParams_whenCreatingOrderHistory_thenFieldsAreStored() {
        OrderHistoryItem item = buildItem(COMPANY_ID);
        OrderHistory history = buildHistory("user-1", 150.0, List.of(item));

        assertEquals("user-1", history.getUserId());
        assertEquals(150.0, history.getTotalPaid(), 0.001);
        assertEquals(1, history.getItems().size());
        assertNotNull(history.getReceiptId());
        assertNotNull(history.getPurchaseDate());
    }

    // ── Construction: negative ────────────────────────────────────

    @Test
    void givenBlankReceiptId_whenCreatingOrderHistory_thenThrowsIllegalArgumentException() {
        OrderHistoryItem item = buildItem(COMPANY_ID);
        assertThrows(IllegalArgumentException.class,
                () -> new OrderHistory("  ", "user-1", LocalDateTime.now(), 10.0, List.of(item)));
    }

    @Test
    void givenBlankUserId_whenCreatingOrderHistory_thenThrowsIllegalArgumentException() {
        OrderHistoryItem item = buildItem(COMPANY_ID);
        assertThrows(IllegalArgumentException.class,
                () -> new OrderHistory(UUID.randomUUID().toString(), "", LocalDateTime.now(), 10.0, List.of(item)));
    }

    @Test
    void givenNullPurchaseDate_whenCreatingOrderHistory_thenThrowsIllegalArgumentException() {
        OrderHistoryItem item = buildItem(COMPANY_ID);
        assertThrows(IllegalArgumentException.class,
                () -> new OrderHistory(UUID.randomUUID().toString(), "user-1", null, 10.0, List.of(item)));
    }

    @Test
    void givenNegativeTotalPaid_whenCreatingOrderHistory_thenThrowsIllegalArgumentException() {
        OrderHistoryItem item = buildItem(COMPANY_ID);
        assertThrows(IllegalArgumentException.class,
                () -> new OrderHistory(UUID.randomUUID().toString(), "user-1", LocalDateTime.now(), -1.0, List.of(item)));
    }

    @Test
    void givenEmptyItemsList_whenCreatingOrderHistory_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new OrderHistory(UUID.randomUUID().toString(), "user-1", LocalDateTime.now(), 0.0, List.of()));
    }

    @Test
    void givenNullItemsList_whenCreatingOrderHistory_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new OrderHistory(UUID.randomUUID().toString(), "user-1", LocalDateTime.now(), 0.0, null));
    }

    // ── containsItemFromCompany ───────────────────────────────────

    @Test
    void givenHistoryWithItemFromCompany_whenChecking_thenReturnsTrue() {
        OrderHistory history = buildHistory("user-1", 150.0, List.of(buildItem(COMPANY_ID)));

        assertTrue(history.containsItemFromCompany(COMPANY_ID));
    }

    @Test
    void givenHistoryWithNoItemFromCompany_whenChecking_thenReturnsFalse() {
        OrderHistory history = buildHistory("user-1", 150.0, List.of(buildItem("other-company")));

        assertFalse(history.containsItemFromCompany(COMPANY_ID));
    }

    @Test
    void givenHistoryWithMultipleItems_whenOneMatchesCompany_thenReturnsTrue() {
        List<OrderHistoryItem> items = List.of(
                buildItem("company-other"),
                buildItem(COMPANY_ID));
        OrderHistory history = buildHistory("user-1", 200.0, items);

        assertTrue(history.containsItemFromCompany(COMPANY_ID));
    }

    // ── Immutability of items list ────────────────────────────────

    @Test
    void givenOrderHistory_whenModifyingReturnedItemsList_thenThrowsUnsupportedOperationException() {
        OrderHistory history = buildHistory("user-1", 150.0, List.of(buildItem(COMPANY_ID)));

        assertThrows(UnsupportedOperationException.class,
                () -> history.getItems().add(buildItem("attacker")));
    }

    // ── OrderHistoryItem value object ─────────────────────────────

    @Test
    void givenValidOrderHistoryItem_whenCreated_thenFieldsAreStored() {
        LocalDateTime eventDate = LocalDateTime.now().plusDays(5);
        OrderHistoryItem item = new OrderHistoryItem(
                "event-1", "Jazz Night", eventDate, COMPANY_ID, "Jazz Co.", "General", "GA", 50.0);

        assertEquals("event-1", item.getEventId());
        assertEquals("Jazz Night", item.getEventTitle());
        assertEquals(COMPANY_ID, item.getCompanyId());
        assertEquals("Jazz Co.", item.getCompanyName());
        assertEquals("GA", item.getSeatLabel());
        assertEquals(50.0, item.getPricePaid(), 0.001);
    }

    @Test
    void givenNegativePricePaid_whenCreatingOrderHistoryItem_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                new OrderHistoryItem("e-1", "Title", LocalDateTime.now(),
                        COMPANY_ID, "Co.", "Zone", null, -5.0));
    }

    @Test
    void givenBlankEventTitle_whenCreatingOrderHistoryItem_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                new OrderHistoryItem("e-1", "  ", LocalDateTime.now(),
                        COMPANY_ID, "Co.", "Zone", null, 10.0));
    }
}
