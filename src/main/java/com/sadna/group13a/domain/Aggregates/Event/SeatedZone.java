package com.sadna.group13a.domain.Aggregates.Event;

import com.sadna.group13a.domain.shared.SeatStatus;
import com.sadna.group13a.domain.shared.ZoneType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A zone composed of individual assigned seats.
 */
public class SeatedZone extends Zone {

    private final List<Seat> seats;

    public SeatedZone(String id, String name, double basePrice, List<Seat> seats) {
        super(id, name, ZoneType.SEATED, basePrice);
        if (seats == null || seats.isEmpty()) {
            throw new IllegalArgumentException("Seated zone must have at least one seat");
        }
        this.seats = new ArrayList<>(seats);
    }

    public List<Seat> getSeats() {
        return Collections.unmodifiableList(seats);
    }

    public Optional<Seat> findSeatById(String seatId) {
        return seats.stream()
                .filter(s -> s.getId().equals(seatId))
                .findFirst();
    }

    @Override
    public int getMaxCapacity() {
        return seats.size();
    }

    @Override
    public int getAvailableSeatCount() {
        return (int) seats.stream()
                .filter(s -> s.getEffectiveStatus() == SeatStatus.AVAILABLE)
                .count();
    }

    @Override
    public int getActiveHoldCount() {
        return (int) seats.stream()
                .filter(s -> s.getEffectiveStatus() == SeatStatus.HELD)
                .count();
    }

    @Override
    public int getSoldCount() {
        return (int) seats.stream()
                .filter(s -> s.getEffectiveStatus() == SeatStatus.SOLD)
                .count();
    }
}