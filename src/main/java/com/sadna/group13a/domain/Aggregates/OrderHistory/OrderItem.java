package com.sadna.group13a.domain.Aggregates.OrderHistory;

import java.util.Objects;

/**
 * Value Object representing a single ticket/item in the order.
 */
public class OrderItem {
    private final String eventId;
    private final String zoneId;
    private final String seatId; // Null for standing zones
    private final double basePrice;

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