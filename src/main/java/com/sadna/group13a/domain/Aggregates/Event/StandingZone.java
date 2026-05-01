package com.sadna.group13a.domain.Aggregates.Event;

import com.sadna.group13a.domain.shared.SeatUnavailableException;
import com.sadna.group13a.domain.shared.ZoneType;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A zone based on total capacity (e.g., General Admission), rather than assigned seats.
 */
public class StandingZone extends Zone {

    private final int maxCapacity;
    private final List<StandingHold> standingHolds;
    private int soldCount;

    public StandingZone(String id, String name, double basePrice, int maxCapacity) {
        super(id, name, ZoneType.STANDING, basePrice);
        if (maxCapacity <= 0) {
            throw new IllegalArgumentException("Standing zone capacity must be positive");
        }
        this.maxCapacity = maxCapacity;
        this.standingHolds = new ArrayList<>();
        this.soldCount = 0;
    }

    @Override
    public int getMaxCapacity() {
        return maxCapacity;
    }

    @Override
    public synchronized int getAvailableSeatCount() {
        purgeExpiredHolds();
        return maxCapacity - standingHolds.size() - soldCount;
    }

    @Override
    public synchronized int getActiveHoldCount() {
        purgeExpiredHolds();
        return standingHolds.size();
    }

    @Override
    public synchronized int getSoldCount() {
        return soldCount;
    }

    public synchronized void holdStandingSpot(String userId) {
        holdStandingSpot(userId, Seat.DEFAULT_HOLD_DURATION);
    }

    public synchronized void holdStandingSpot(String userId, Duration duration) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId cannot be null or blank");
        }

        purgeExpiredHolds();

        if (standingHolds.size() + soldCount >= maxCapacity) {
            throw new SeatUnavailableException(getId(), "standing zone is at capacity");
        }

        standingHolds.add(new StandingHold(userId, Instant.now().plus(duration)));
    }

    public synchronized void sellStandingSpot(String userId) {
        purgeExpiredHolds();

        for (int i = 0; i < standingHolds.size(); i++) {
            if (standingHolds.get(i).userId.equals(userId)) {
                standingHolds.remove(i);
                soldCount++;
                return;
            }
        }
        throw new SeatUnavailableException(getId(), "user does not have an active hold in this zone");
    }

    public synchronized void releaseStandingSpot(String userId) {
        for (int i = 0; i < standingHolds.size(); i++) {
            if (standingHolds.get(i).userId.equals(userId)) {
                standingHolds.remove(i);
                return; // Release exactly one hold
            }
        }
    }

    private synchronized void purgeExpiredHolds() {
        Instant now = Instant.now();
        standingHolds.removeIf(h -> now.isAfter(h.expiresAt));
    }

    static class StandingHold {
        final String userId;
        final Instant expiresAt;

        StandingHold(String userId, Instant expiresAt) {
            this.userId = userId;
            this.expiresAt = expiresAt;
        }
    }
}