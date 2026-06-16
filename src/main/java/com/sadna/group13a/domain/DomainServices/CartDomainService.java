package com.sadna.group13a.domain.DomainServices;

import com.sadna.group13a.domain.Aggregates.Event.Event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Domain Service — pure Java, no Spring annotations.
 * Encapsulates the atomic seat-reservation rule: all requested seats in a zone
 * must be reserved together, or none are reserved (automatic rollback on any
 * individual failure).
 */
public class CartDomainService {

    private static final Logger logger = LoggerFactory.getLogger(CartDomainService.class);

    /**
     * Reserves all seats in {@code seatsToReserve} atomically on the given event.
     * If any individual reservation fails, all already-reserved seats are released
     * before re-throwing the exception.
     *
     * @param event          the event whose seat map will be mutated
     * @param zoneId         the zone to reserve seats in
     * @param seatsToReserve seat IDs to reserve (null entry = next available standing spot)
     * @param userId         the buyer holding the seats
     * @throws RuntimeException re-thrown from the first failing reservation, after rollback
     */
    public void reserveSeatsAtomically(Event event, String zoneId, List<String> seatsToReserve, String userId) {
        List<String> reserved = new ArrayList<>();
        try {
            for (String seatId : seatsToReserve) {
                event.reserveSeat(zoneId, seatId, userId);
                reserved.add(seatId);
            }
        } catch (Exception e) {
            logger.warn("Batch reservation failed for user '{}' in zone '{}': {} — rolling back {} seat(s).",
                    userId, zoneId, e.getMessage(), reserved.size());
            for (String id : reserved) {
                try {
                    event.releaseItem(zoneId, id, userId);
                } catch (Exception re) {
                    logger.warn("Failed to release seat '{}' during batch rollback: {}", id, re.getMessage());
                }
            }
            throw e;
        }
    }
}
