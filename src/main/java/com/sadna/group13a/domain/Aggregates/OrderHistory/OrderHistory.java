package com.sadna.group13a.domain.Aggregates.OrderHistory;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregate Root representing a finalized, immutable purchase receipt.
 * Once created, an OrderHistory cannot be altered.
 */
@Entity
@Table(name = "order_history")
public class OrderHistory {

    @Id
    @Column(name = "receipt_id", nullable = false)
    private String receiptId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "purchase_date", nullable = false)
    private LocalDateTime purchaseDate;

    @Column(name = "total_paid", nullable = false)
    private double totalPaid;

    /** Payment-provider transaction id — required for issuing refunds on event cancellation. */
    @Column(name = "transaction_id")
    private String transactionId;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "receipt_id")
    private List<OrderHistoryItem> items;

    /** Required by JPA. Do not use in business code. */
    protected OrderHistory() {}

    public OrderHistory(String receiptId, String userId, LocalDateTime purchaseDate, double totalPaid,
                        String transactionId, List<OrderHistoryItem> items) {
        if (receiptId == null || receiptId.isBlank()) throw new IllegalArgumentException("receiptId cannot be blank");
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId cannot be blank");
        if (purchaseDate == null) throw new IllegalArgumentException("purchaseDate cannot be null");
        if (totalPaid < 0) throw new IllegalArgumentException("totalPaid cannot be negative");
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("items cannot be null or empty");

        this.receiptId = receiptId;
        this.userId = userId;
        this.purchaseDate = purchaseDate;
        this.totalPaid = totalPaid;
        this.transactionId = transactionId;
        this.items = new ArrayList<>(items);
    }

    /** Convenience constructor for records without a known payment transaction id. */
    public OrderHistory(String receiptId, String userId, LocalDateTime purchaseDate, double totalPaid, List<OrderHistoryItem> items) {
        this(receiptId, userId, purchaseDate, totalPaid, null, items);
    }

    public String getReceiptId() { return receiptId; }
    public String getUserId() { return userId; }
    public LocalDateTime getPurchaseDate() { return purchaseDate; }
    public double getTotalPaid() { return totalPaid; }
    public String getTransactionId() { return transactionId; }

    public List<OrderHistoryItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public boolean containsItemFromCompany(String companyId) {
        return items.stream().anyMatch(i -> i.getCompanyId().equals(companyId));
    }
}
