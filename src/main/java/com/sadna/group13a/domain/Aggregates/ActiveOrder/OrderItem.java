package com.sadna.group13a.domain.Aggregates.ActiveOrder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

/**
 * Value Object representing a single ticket/item in the order.
 * Persisted as an entity to support @OneToMany from ActiveOrder.
 */
@Entity
@Table(name = "order_items")
public class OrderItem {

    /** Surrogate primary key — the business identity is the (eventId, zoneId, seatId) triple. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "item_id")
    private String itemId;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "zone_id", nullable = false)
    private String zoneId;

    @Column(name = "seat_id")
    private String seatId; // null for standing zones

    @Column(name = "base_price", nullable = false)
    private double basePrice;

    /** Required by JPA. Do not use in business code. */
    protected OrderItem() {}

    public OrderItem(String eventId, String zoneId, String seatId, double basePrice) {
        if (eventId == null || eventId.isBlank()) throw new IllegalArgumentException("eventId cannot be blank");
        if (zoneId == null || zoneId.isBlank()) throw new IllegalArgumentException("zoneId cannot be blank");
        if (basePrice < 0) throw new IllegalArgumentException("basePrice cannot be negative");

        this.eventId = eventId;
        this.zoneId = zoneId;
        this.seatId = seatId;
        this.basePrice = basePrice;
    }

    public String getEventId() { return eventId; }
    public String getZoneId() { return zoneId; }
    public String getSeatId() { return seatId; }
    public double getBasePrice() { return basePrice; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderItem orderItem = (OrderItem) o;
        return Double.compare(orderItem.basePrice, basePrice) == 0 &&
                eventId.equals(orderItem.eventId) &&
                zoneId.equals(orderItem.zoneId) &&
                Objects.equals(seatId, orderItem.seatId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, zoneId, seatId, basePrice);
    }
}
