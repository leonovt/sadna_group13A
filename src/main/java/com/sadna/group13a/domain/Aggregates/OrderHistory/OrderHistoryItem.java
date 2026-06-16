package com.sadna.group13a.domain.Aggregates.OrderHistory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Value Object representing a single ticket within a finalized purchase receipt.
 * All details are copied at purchase time to remain accurate even if the underlying
 * Event, Venue, or Company is later modified or deleted.
 */
@Entity
@Table(name = "order_history_items")
public class OrderHistoryItem {

    /** Surrogate primary key — receipt items have no natural single-column identity. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "item_id")
    private String itemId;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "event_title", nullable = false)
    private String eventTitle;

    @Column(name = "event_date", nullable = false)
    private LocalDateTime eventDate;

    @Column(name = "company_id", nullable = false)
    private String companyId;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "zone_name", nullable = false)
    private String zoneName;

    @Column(name = "seat_label")
    private String seatLabel; // nullable for standing admission

    @Column(name = "price_paid", nullable = false)
    private double pricePaid;

    /** Ticket code issued by the external ticket supplier. Stored for potential refund/validation. */
    @Column(name = "ticket_code")
    private String ticketCode;

    /** Required by JPA. Do not use in business code. */
    protected OrderHistoryItem() {}

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
    public String getTicketCode() { return ticketCode; }

    public void setTicketCode(String ticketCode) {
        this.ticketCode = ticketCode;
    }

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
