package com.sadna.group13a.domain.order;

import com.sadna.group13a.domain.shared.DomainException;
import com.sadna.group13a.domain.shared.UserType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregate root representing a user's active shopping cart and checkout session.
 */
public class ActiveOrder {
    public static final int EXPIRATION_MINUTES = 10;

    private final String orderId;
    private final String userId;
    private final UserType userType;
    private OrderStatus status;
    private final List<OrderItem> items;
    private final LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private String transactionId;

    public ActiveOrder(String orderId, String userId, UserType userType) {
        if (orderId == null || orderId.isBlank()) throw new IllegalArgumentException("orderId cannot be blank");
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId cannot be blank");
        if (userType == null) throw new IllegalArgumentException("userType cannot be null");

        this.orderId = orderId;
        this.userId = userId;
        this.userType = userType;
        this.status = OrderStatus.DRAFT;
        this.items = new ArrayList<>();
        this.createdAt = LocalDateTime.now();
        this.expiresAt = null; // Timer starts when first item is added
        this.transactionId = null;
    }

    public String getOrderId() { return orderId; }
    public String getUserId() { return userId; }
    public UserType getUserType() { return userType; }
    public OrderStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public String getTransactionId() { return transactionId; }
    public List<OrderItem> getItems() { return Collections.unmodifiableList(items); }

    public boolean isExpired() {
        if (status != OrderStatus.DRAFT || expiresAt == null) return false;
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public void addItem(OrderItem item) {
        if (isExpired()) {
            this.status = OrderStatus.CANCELLED;
            throw new DomainException("Order has expired. Cannot add items.");
        }
        if (status != OrderStatus.DRAFT) {
            throw new DomainException("Cannot add items to an order that is not in DRAFT state");
        }
        if (item == null) {
            throw new IllegalArgumentException("Item cannot be null");
        }
        
        items.add(item);
        
        // Start the timer when the first item is added
        if (expiresAt == null) {
            expiresAt = LocalDateTime.now().plusMinutes(EXPIRATION_MINUTES);
        }
    }

    public void removeItem(OrderItem item) {
        if (isExpired()) {
            this.status = OrderStatus.CANCELLED;
            throw new DomainException("Order has expired. Cannot remove items.");
        }
        if (status != OrderStatus.DRAFT) {
            throw new DomainException("Cannot remove items from an order that is not in DRAFT state");
        }
        items.remove(item);
    }

    public double calculateTotalBasePrice() {
        if (isExpired()) {
            this.status = OrderStatus.CANCELLED;
        }
        return items.stream().mapToDouble(OrderItem::getBasePrice).sum();
    }

    public void complete(String transactionId) {
        if (isExpired()) {
            this.status = OrderStatus.CANCELLED;
            throw new DomainException("Order has expired and cannot be completed");
        }
        if (status != OrderStatus.DRAFT) {
            throw new DomainException("Only DRAFT orders can be completed");
        }
        if (items.isEmpty()) {
            throw new DomainException("Cannot complete an empty order");
        }
        if (transactionId == null || transactionId.isBlank()) {
            throw new IllegalArgumentException("Transaction ID is required to complete an order");
        }
        this.transactionId = transactionId;
        this.status = OrderStatus.COMPLETED;
    }

    public void cancel() {
        if (status != OrderStatus.DRAFT) {
            throw new DomainException("Only DRAFT orders can be cancelled");
        }
        this.status = OrderStatus.CANCELLED;
    }
}
