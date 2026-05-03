package com.sadna.group13a.domain.Aggregates.Event;

import com.sadna.group13a.domain.shared.DomainException;
import com.sadna.group13a.domain.shared.EntityNotFoundException;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Aggregate Root for the Event aggregate.
 * From UML: Event (Root) → VenueMap → Zone → Seat.
 *
 * An Event represents a scheduled occurrence managed by a ProductionCompany.
 * It owns a VenueMap that defines the venue layout and seat/standing capacity.
 * PurchasePolicy and DiscountPolicy are referenced by ID (separate aggregate boundary).
 */
public class Event {

    private final String id;
    private String title;
    private String description;
    private String companyId;       // owning ProductionCompany
    private LocalDateTime eventDate;
    private String category;
    private VenueMap venueMap;
    private boolean published;      // whether the event is visible to buyers
    private EventSaleMode saleMode; // REGULAR or RAFFLE

    public Event(String id, String title, String description,
                 String companyId, LocalDateTime eventDate, String category) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Event id cannot be null or blank");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Event title cannot be null or blank");
        }
        if (companyId == null || companyId.isBlank()) {
            throw new IllegalArgumentException("Company id cannot be null or blank");
        }
        if (eventDate == null) {
            throw new IllegalArgumentException("Event date cannot be null");
        }
        this.id = id;
        this.title = title;
        this.description = description;
        this.companyId = companyId;
        this.eventDate = eventDate;
        this.category = category;
        this.venueMap = null;
        this.published = false;
        this.saleMode = EventSaleMode.REGULAR;
    }

    public Event(String title, String description,
                 String companyId, LocalDateTime eventDate, String category) {
        this(UUID.randomUUID().toString(), title, description,
                companyId, eventDate, category);
    }

    // ── Identity & Properties ─────────────────────────────────────

    public String getId() { return id; }

    public String getTitle() { return title; }

    public void setTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Event title cannot be null or blank");
        }
        this.title = title;
    }

    public String getDescription() { return description; }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCompanyId() { return companyId; }

    public LocalDateTime getEventDate() { return eventDate; }

    public void setEventDate(LocalDateTime eventDate) {
        if (eventDate == null) {
            throw new IllegalArgumentException("Event date cannot be null");
        }
        this.eventDate = eventDate;
    }

    public String getCategory() { return category; }

    public void setCategory(String category) {
        this.category = category;
    }

    // ── Sale Mode (Raffle/Lottery) ────────────────────────────────

    public EventSaleMode getSaleMode() { return saleMode; }

    public void setSaleMode(EventSaleMode saleMode) {
        if (published) {
            throw new DomainException("Cannot change sale mode of a published event");
        }
        if (saleMode == null) {
            throw new IllegalArgumentException("Sale mode cannot be null");
        }
        this.saleMode = saleMode;
    }

    // ── Publishing ────────────────────────────────────────────────

    public boolean isPublished() { return published; }

    /**
     * Publishes the event, making it visible to buyers.
     * Requires a venue map to be configured first.
     *
     * @throws DomainException if no venue map is set
     */
    public void publish() {
        if (venueMap == null) {
            throw new DomainException("Cannot publish event without a venue map");
        }
        this.published = true;
    }

    public void unpublish() {
        this.published = false;
    }

    // ── VenueMap ──────────────────────────────────────────────────

    public VenueMap getVenueMap() { return venueMap; }

    /**
     * Assigns or replaces the venue map configuration for this event.
     *
     * @throws DomainException if the event is already published
     */
    public void setVenueMap(VenueMap venueMap) {
        if (published) {
            throw new DomainException(
                    "Cannot change venue map of a published event");
        }
        this.venueMap = venueMap;
    }

    // ── Convenience Delegations ───────────────────────────────────

    /**
     * Finds a zone by ID, delegating to the venue map.
     *
     * @throws DomainException if no venue map is configured
     * @throws EntityNotFoundException if the zone is not found
     */
    public Zone getZoneById(String zoneId) {
        requireVenueMap();
        return venueMap.getZoneById(zoneId);
    }

    /**
     * Returns the total available capacity across all zones.
     *
     * @throws DomainException if no venue map is configured
     */
    public int getTotalAvailable() {
        requireVenueMap();
        return venueMap.getTotalAvailable();
    }

    private void requireVenueMap() {
        if (venueMap == null) {
            throw new DomainException(
                    "Event does not have a venue map configured");
        }
    }
}