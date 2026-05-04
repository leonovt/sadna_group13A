package com.sadna.group13a.domain.Aggregates.ActiveOrder;

import com.sadna.group13a.domain.shared.OrderStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregate Root for a user's active shopping cart.
 * Holds item reservations until checkout completes or the cart expires.
 */
public class ActiveOrder {

    private final String id;
    private final String userId;
    private final List<OrderItem> items;
    private OrderStatus status;
    private final LocalDateTime createdAt;
    private LocalDateTime expiresAt;

    public ActiveOrder(String id, String userId) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id cannot be blank");
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId cannot be blank");
        this.id = id;
        this.userId = userId;
        this.items = new ArrayList<>();
        this.status = OrderStatus.OPEN;
        this.createdAt = LocalDateTime.now();
        this.expiresAt = createdAt.plusMinutes(30);
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public OrderStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public void addItem(OrderItem item) {
        if (item == null) throw new IllegalArgumentException("Item cannot be null");
        if (status != OrderStatus.OPEN) throw new IllegalStateException("Cannot modify a non-draft order");
        items.add(item);
    }

    public void removeItem(OrderItem item) {
        if (status != OrderStatus.OPEN) throw new IllegalStateException("Cannot modify a non-draft order");
        items.remove(item);
    }

    public void submit() {
        if (status != OrderStatus.OPEN) throw new IllegalStateException("Order is not in OPEN state");
        if (items.isEmpty()) throw new IllegalStateException("Cannot submit an empty order");
        this.status = OrderStatus.PENDING_PAYMENT;
    }

    public void cancel() {
        if (status == OrderStatus.COMPLETED) throw new IllegalStateException("Cannot cancel a completed order");
        this.status = OrderStatus.CANCELLED;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public double getTotalPrice() {
        return items.stream().mapToDouble(OrderItem::getBasePrice).sum();
    }
}
