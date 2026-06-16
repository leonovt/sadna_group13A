package com.sadna.group13a.domain.DomainServices;

import com.sadna.group13a.domain.Aggregates.Event.Seat;
import com.sadna.group13a.domain.Aggregates.Event.SeatedZone;
import com.sadna.group13a.domain.Aggregates.Event.StandingZone;
import com.sadna.group13a.domain.Aggregates.Event.VenueMap;
import com.sadna.group13a.domain.Aggregates.Event.Zone;
import com.sadna.group13a.domain.Aggregates.Event.ZoneType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Domain Service — pure Java, no Spring annotations.
 * Factory responsible for constructing VenueMap aggregates and their child
 * Zone/Seat entities from raw primitive specifications, keeping domain object
 * construction out of the application layer.
 */
public class VenueMapFactory {

    /**
     * Constructs a single Zone from primitive values.
     * For SEATED zones, individual Seat entities are generated with labels
     * "{name} 1", "{name} 2", … so the inventory is ready for per-seat holds.
     *
     * @param name      the zone display name
     * @param type      SEATED or STANDING
     * @param basePrice the base ticket price for this zone
     * @param capacity  number of seats (SEATED) or max standing capacity (STANDING)
     * @return the fully constructed Zone
     */
    public Zone buildZone(String name, ZoneType type, double basePrice, int capacity) {
        String zoneId = UUID.randomUUID().toString();
        if (type == ZoneType.SEATED) {
            List<Seat> seats = new ArrayList<>();
            for (int i = 1; i <= capacity; i++) {
                seats.add(new Seat(UUID.randomUUID().toString(), name + " " + i));
            }
            return new SeatedZone(zoneId, name, basePrice, seats);
        }
        return new StandingZone(zoneId, name, basePrice, capacity);
    }

    /**
     * Wraps a list of already-built zones into a VenueMap aggregate.
     *
     * @param venueName the venue display name
     * @param zones     the zones that make up this venue
     * @return the constructed VenueMap
     */
    public VenueMap build(String venueName, List<Zone> zones) {
        return new VenueMap(UUID.randomUUID().toString(), venueName, zones);
    }
}
