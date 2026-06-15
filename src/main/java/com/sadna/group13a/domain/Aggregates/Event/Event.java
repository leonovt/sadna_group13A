package com.sadna.group13a.domain.Aggregates.Event;

import com.sadna.group13a.domain.policies.discount.NoDiscountPolicy;
import com.sadna.group13a.domain.policies.purchase.AllowAllPolicy;
import com.sadna.group13a.domain.shared.DiscountPolicy;
import com.sadna.group13a.domain.shared.DomainException;
import com.sadna.group13a.domain.shared.PurchasePolicy;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "events")
public class Event {

    @Id
    private String id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "company_id", nullable = false)
    private String companyId;

    @Column(name = "event_date", nullable = false)
    private LocalDateTime eventDate;

    @Column
    private String category;

    @Column
    private String artist;

    @Column
    private String location;

    @Column(nullable = false)
    private boolean published;

    @Enumerated(EnumType.STRING)
    @Column(name = "sale_mode", nullable = false)
    private EventSaleMode saleMode;

    @Version
    private long version;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "venue_map_id")
    private VenueMap venueMap;

    @Transient
    private PurchasePolicy purchasePolicy;

    @Transient
    private DiscountPolicy discountPolicy;

    protected Event() {}

    @PostLoad
    private void onLoad() {
        if (purchasePolicy == null) purchasePolicy = new AllowAllPolicy();
        if (discountPolicy == null) discountPolicy = new NoDiscountPolicy();
    }

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
        this.artist = null;
        this.location = null;
        this.venueMap = null;
        this.published = false;
        this.saleMode = EventSaleMode.REGULAR;
        this.purchasePolicy = new AllowAllPolicy();
        this.discountPolicy = new NoDiscountPolicy();
    }

    public Event(String title, String description,
                 String companyId, LocalDateTime eventDate, String category) {
        this(UUID.randomUUID().toString(), title, description,
                companyId, eventDate, category);
    }

    // ── Identity & Properties ─────────────────────────────────────

    public String getId() { return id; }

    public long getVersion() { return version; }

    public String getTitle() { return title; }

    public void setTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Event title cannot be null or blank");
        }
        this.title = title;
        version++;
    }

    public String getDescription() { return description; }

    public void setDescription(String description) {
        this.description = description;
        version++;
    }

    public String getCompanyId() { return companyId; }

    public LocalDateTime getEventDate() { return eventDate; }

    public void setEventDate(LocalDateTime eventDate) {
        if (eventDate == null) {
            throw new IllegalArgumentException("Event date cannot be null");
        }
        this.eventDate = eventDate;
        version++;
    }

    public String getCategory() { return category; }

    public void setCategory(String category) {
        this.category = category;
        version++;
    }

    public String getArtist() { return artist; }

    public void setArtist(String artist) {
        this.artist = artist;
        version++;
    }

    public String getLocation() { return location; }

    public void setLocation(String location) {
        this.location = location;
        version++;
    }

    // ── Sale Mode ────────────────────────────────────────────────

    public EventSaleMode getSaleMode() { return saleMode; }

    public void setSaleMode(EventSaleMode saleMode) {
        if (saleMode == null) {
            throw new IllegalArgumentException("Sale mode cannot be null");
        }
        this.saleMode = saleMode;
        version++;
    }

    // ── Publishing ────────────────────────────────────────────────

    public boolean isPublished() { return published; }

    public void publish() {
        if (venueMap == null) {
            throw new DomainException("Cannot publish event without a venue map");
        }
        this.published = true;
        version++;
    }

    public void unpublish() {
        this.published = false;
        version++;
    }

    // ── VenueMap ──────────────────────────────────────────────────

    public VenueMap getVenueMap() { return venueMap; }

    public void setVenueMap(VenueMap venueMap) {
        if (published) {
            throw new DomainException("Cannot change venue map of a published event");
        }
        this.venueMap = venueMap;
        version++;
    }

    // ── Policies ──────────────────────────────────────────────────

    public PurchasePolicy getPurchasePolicy() {
        return purchasePolicy;
    }

    public void setPurchasePolicy(PurchasePolicy policy) {
        if (policy == null) throw new IllegalArgumentException("Purchase policy cannot be null");
        this.purchasePolicy = policy;
        version++;
    }

    public DiscountPolicy getDiscountPolicy() {
        return discountPolicy;
    }

    public void setDiscountPolicy(DiscountPolicy policy) {
        if (policy == null) throw new IllegalArgumentException("Discount policy cannot be null");
        this.discountPolicy = policy;
        version++;
    }

    // ── Seat State Transitions (all mutations through root) ───────

    public void reserveSeat(String zoneId, String seatId, String userId) {
        Zone zone = getZoneById(zoneId);
        if (zone instanceof SeatedZone sz) {
            sz.findSeatById(seatId)
                    .orElseThrow(() -> new IllegalArgumentException("Seat not found: " + seatId))
                    .hold(userId);
        } else if (zone instanceof StandingZone stz) {
            stz.holdStandingSpot(userId);
        }
        version++;
    }

    public String sellItem(String zoneId, String seatId, String userId) {
        Zone zone = getZoneById(zoneId);
        if (zone instanceof SeatedZone sz) {
            Seat seat = sz.findSeatById(seatId)
                    .orElseThrow(() -> new DomainException(
                            "Seat " + seatId + " not found in zone " + zoneId));
            seat.sell(userId);
            version++;
            return seat.getLabel();
        } else if (zone instanceof StandingZone stz) {
            stz.sellStandingSpot(userId);
            version++;
        }
        return null;
    }

    public void unsellItem(String zoneId, String seatId) {
        Zone zone = getZoneById(zoneId);
        if (zone instanceof SeatedZone sz) {
            sz.findSeatById(seatId).ifPresent(Seat::unsell);
        } else if (zone instanceof StandingZone stz) {
            stz.unsellStandingSpot();
        }
        version++;
    }

    public void releaseItem(String zoneId, String seatId, String userId) {
        Zone zone = getZoneById(zoneId);
        if (zone instanceof SeatedZone sz) {
            sz.findSeatById(seatId).ifPresent(Seat::release);
        } else if (zone instanceof StandingZone stz) {
            stz.releaseStandingSpot(userId);
        }
        version++;
    }

    // ── Convenience Delegations (read-only) ───────────────────────

    public Zone getZoneById(String zoneId) {
        requireVenueMap();
        return venueMap.getZoneById(zoneId);
    }

    public double getZoneBasePrice(String zoneId) {
        return getZoneById(zoneId).getBasePrice();
    }

    public String getZoneName(String zoneId) {
        return getZoneById(zoneId).getName();
    }

    public int getTotalAvailable() {
        requireVenueMap();
        return venueMap.getTotalAvailable();
    }

    private void requireVenueMap() {
        if (venueMap == null) {
            throw new DomainException("Event does not have a venue map configured");
        }
    }
}
