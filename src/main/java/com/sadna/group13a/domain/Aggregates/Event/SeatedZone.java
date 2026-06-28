package com.sadna.group13a.domain.Aggregates.Event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
/**
 * A zone composed of individual assigned seats.
 */
public class SeatedZone extends Zone {

    private final List<Seat> seats;
    private final int rows;
    private final int columns;

    public SeatedZone(String id, String name, double basePrice, List<Seat> seats) {
        this(id, name, basePrice, seats, 0, 0);
    }

    @JsonCreator
    public SeatedZone(@JsonProperty("id") String id, @JsonProperty("name") String name,
                       @JsonProperty("basePrice") double basePrice, @JsonProperty("seats") List<Seat> seats,
                       @JsonProperty("rows") int rows, @JsonProperty("columns") int columns) {
        super(id, name, ZoneType.SEATED, basePrice);
        if (seats == null || seats.isEmpty()) {
            throw new IllegalArgumentException("Seated zone must have at least one seat");
        }
        this.seats = new ArrayList<>(seats);
        this.rows = rows;
        this.columns = columns;
    }

    public int getRows() { return rows; }
    public int getColumns() { return columns; }

    public List<Seat> getSeats() {
        return Collections.unmodifiableList(seats);
    }

    public void addSeats(List<Seat> newSeats) {
        if (newSeats == null || newSeats.isEmpty()) {
            throw new IllegalArgumentException("Must add at least one seat");
        }
        this.seats.addAll(newSeats);
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
