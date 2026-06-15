package com.sadna.group13a.domain.Aggregates.Event;

import com.sadna.group13a.domain.shared.EntityNotFoundException;
import jakarta.persistence.*;

import java.util.*;

@Entity
@Table(name = "venue_maps")
public class VenueMap {

    @Id
    private String id;

    @Column(name = "venue_name", nullable = false)
    private String venueName;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "venue_map_id")
    private List<Zone> zones = new ArrayList<>();

    protected VenueMap() {}

    public VenueMap(String id, String venueName, List<Zone> zones) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("VenueMap id cannot be null or blank");
        }
        if (venueName == null || venueName.isBlank()) {
            throw new IllegalArgumentException("Venue name cannot be null or blank");
        }
        if (zones == null || zones.isEmpty()) {
            throw new IllegalArgumentException("VenueMap must have at least one zone");
        }
        this.id = id;
        this.venueName = venueName;
        this.zones = new ArrayList<>(zones);
    }

    public VenueMap(String id, String venueName) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("VenueMap id cannot be null or blank");
        }
        if (venueName == null || venueName.isBlank()) {
            throw new IllegalArgumentException("Venue name cannot be null or blank");
        }
        this.id = id;
        this.venueName = venueName;
        this.zones = new ArrayList<>();
    }

    public String getId() { return id; }

    public String getVenueName() { return venueName; }

    public List<Zone> getZones() {
        return Collections.unmodifiableList(zones);
    }

    public void addZone(Zone zone) {
        if (zone == null) {
            throw new IllegalArgumentException("Zone cannot be null");
        }
        zones.add(zone);
    }

    public Zone getZoneById(String zoneId) {
        return zones.stream()
                .filter(z -> z.getId().equals(zoneId))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Zone", zoneId));
    }

    public int getTotalCapacity() {
        return zones.stream().mapToInt(Zone::getMaxCapacity).sum();
    }

    public int getTotalAvailable() {
        return zones.stream().mapToInt(Zone::getAvailableSeatCount).sum();
    }
}
