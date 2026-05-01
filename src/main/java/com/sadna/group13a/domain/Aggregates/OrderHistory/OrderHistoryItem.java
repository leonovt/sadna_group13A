package com.sadna.group13a.domain.Aggregates.OrderHistory;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Value Object representing a single ticket within a finalized purchase receipt.
 * All details are copied here to ensure mathematical immutability even if 
 * the underlying Event, Venue, or Company is later modified or deleted.
 */
public class OrderHistoryItem {
    private final String eventId;
    private final String eventTitle;
    private final LocalDateTime eventDate;
    private final String companyId;
    private final String companyName;
    private final String zoneName;
    private final String seatLabel; // nullable for standing admission
    private final double pricePaid;

    public OrderHistoryItem(String eventId, String eventTitle, LocalDateTime eventDate,
                            String companyId, String companyName, String zoneName,
                            String seatLabel, double pricePaid) {
        if (eventId == null || eventId.isBlank()) throw new IllegalArgumentException("eventId cannot be blank");
        if (eventTitle == null || eventTitle.isBlank()) throw new IllegalArgumentException("eventTitle cannot be blank");
        if (eventDate == null) throw new IllegalArgumentException("eventDate cannot be null");
        if (companyId == null || companyId.isBlank()) throw new IllegalArgumentException("companyId cannot be blank");
        if (companyName == null || companyName.isBlank()) throw new IllegalArgumentException("companyName cannot be blank");
        if (zoneName == null || zoneName.isBlank()) throw new IllegalArgumentException("zoneName cannot be blank");
        if (pricePaid < 0) throw new IllegalArgumentException("pricePaid cannot be negative");

        this.eventId = eventId;
        this.eventTitle = eventTitle;
        this.eventDate = eventDate;
        this.companyId = companyId;
        this.companyName = companyName;
        this.zoneName = zoneName;
        this.seatLabel = seatLabel;
        this.pricePaid = pricePaid;
    }

    public String getEventId() { return eventId; }
    public String getEventTitle() { return eventTitle; }
    public LocalDateTime getEventDate() { return eventDate; }
    public String getCompanyId() { return companyId; }
    public String getCompanyName() { return companyName; }
    public String getZoneName() { return zoneName; }
    public String getSeatLabel() { return seatLabel; }
    public double getPricePaid() { return pricePaid; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderHistoryItem that = (OrderHistoryItem) o;
        return Double.compare(that.pricePaid, pricePaid) == 0 &&
                eventId.equals(that.eventId) &&
                eventTitle.equals(that.eventTitle) &&
                eventDate.equals(that.eventDate) &&
                companyId.equals(that.companyId) &&
                companyName.equals(that.companyName) &&
                zoneName.equals(that.zoneName) &&
                Objects.equals(seatLabel, that.seatLabel);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, eventTitle, eventDate, companyId, companyName, zoneName, seatLabel, pricePaid);
    }
}