package com.sadna.group13a.domain.order;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregate Root representing a finalized, mathematically immutable purchase receipt.
 * Once created, an OrderHistory cannot be altered. It guarantees that historical
 * purchase records are preserved exactly as they occurred.
 */
public class OrderHistory {
    private final String receiptId;
    private final String userId;
    private final LocalDateTime purchaseDate;
    private final double totalPaid;
    private final List<OrderHistoryItem> items;

    public OrderHistory(String receiptId, String userId, LocalDateTime purchaseDate, double totalPaid, List<OrderHistoryItem> items) {
        if (receiptId == null || receiptId.isBlank()) throw new IllegalArgumentException("receiptId cannot be blank");
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId cannot be blank");
        if (purchaseDate == null) throw new IllegalArgumentException("purchaseDate cannot be null");
        if (totalPaid < 0) throw new IllegalArgumentException("totalPaid cannot be negative");
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("items cannot be null or empty");

        this.receiptId = receiptId;
        this.userId = userId;
        this.purchaseDate = purchaseDate;
        this.totalPaid = totalPaid;
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
    }

    public String getReceiptId() { return receiptId; }
    public String getUserId() { return userId; }
    public LocalDateTime getPurchaseDate() { return purchaseDate; }
    public double getTotalPaid() { return totalPaid; }
    public List<OrderHistoryItem> getItems() { return items; }
}
