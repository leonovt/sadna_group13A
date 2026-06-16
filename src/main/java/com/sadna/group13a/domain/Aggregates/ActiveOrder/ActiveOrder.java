package com.sadna.group13a.domain.Aggregates.ActiveOrder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sadna.group13a.domain.Aggregates.Event.Seat;
import com.sadna.group13a.domain.shared.OrderStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Aggregate Root for a user's active shopping cart.
 * Holds item reservations until checkout completes or the cart expires.
 *
 * The {@code version} field is incremented on every mutation and is used for
 * optimistic-locking conflict detection (analogous to JPA {@code @Version}).
 */
public class ActiveOrder {

    private final String id;
    private final String userId;
    private final List<OrderItem> items;
    private OrderStatus status;
    private final LocalDateTime createdAt;
    private LocalDateTime expiresAt;

    private volatile long version = 0L;

    @JsonCreator
    public ActiveOrder(@JsonProperty("id") String id, @JsonProperty("userId") String userId) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id cannot be blank");
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId cannot be blank");
        this.id = id;
        this.userId = userId;
        this.items = new ArrayList<>();
        this.status = OrderStatus.OPEN;
        this.createdAt = LocalDateTime.now();
        // The cart's lifetime must match the seat-hold lifetime so the order is never
        // "valid" after its held seats have already been released. Seat.DEFAULT_HOLD_DURATION
        // is the single source of truth for the bounded reservation window.
        this.expiresAt = createdAt.plus(Seat.DEFAULT_HOLD_DURATION);
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public OrderStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public long getVersion() { return version; }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public void addItem(OrderItem item) {
        if (item == null) throw new IllegalArgumentException("Item cannot be null");
        if (status != OrderStatus.OPEN) throw new IllegalStateException("Cannot modify a non-draft order");
        items.add(item);
        version++;
    }

    public void removeItem(OrderItem item) {
        if (status != OrderStatus.OPEN) throw new IllegalStateException("Cannot modify a non-draft order");
        items.remove(item);
        version++;
    }

    /**
     * Removes the first item matching the given event/zone/seat key.
     * Uses {@link Objects#equals} for seatId to handle null (standing zones).
     *
     * @return true if an item was removed
     */
    public boolean removeItemByKey(String eventId, String zoneId, String seatId) {
        if (status != OrderStatus.OPEN) throw new IllegalStateException("Cannot modify a non-draft order");
        boolean removed = items.removeIf(i ->
                i.getEventId().equals(eventId) &&
                i.getZoneId().equals(zoneId) &&
                Objects.equals(i.getSeatId(), seatId));
        if (removed) version++;
        return removed;
    }

    public void submit() {
        if (status != OrderStatus.OPEN) throw new IllegalStateException("Order is not in OPEN state");
        if (items.isEmpty()) throw new IllegalStateException("Cannot submit an empty order");
        this.status = OrderStatus.PENDING_PAYMENT;
        version++;
    }

    public void cancel() {
        if (status == OrderStatus.COMPLETED) throw new IllegalStateException("Cannot cancel a completed order");
        this.status = OrderStatus.CANCELLED;
        version++;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public double getTotalPrice() {
        return items.stream().mapToDouble(OrderItem::getBasePrice).sum();
    }
}
