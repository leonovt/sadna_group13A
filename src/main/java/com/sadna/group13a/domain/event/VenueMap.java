package com.sadna.group13a.domain.event;

import com.sadna.group13a.domain.shared.EntityNotFoundException;

import java.util.*;

/**
 * Entity within the Event aggregate — represents the physical layout
 * of a venue, composed of {@link Zone}s.
 *
 * From UML: Event → VenueMap → Zone composition.
 */
public class VenueMap {

    private final String id;
    private final String venueName;
    private final List<Zone> zones;

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
