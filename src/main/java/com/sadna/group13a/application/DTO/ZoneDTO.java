package com.sadna.group13a.application.DTO;

import com.sadna.group13a.domain.Aggregates.Event.ZoneType;

import java.util.List;

public class ZoneDTO {
    private final String id;
    private final String name;
    private final ZoneType type;
    private final double basePrice;
    private final int capacity;
    private final int available;
    private final List<SeatDTO> seats; // null for standing zones

    public ZoneDTO(String id, String name, ZoneType type, double basePrice,
                   int capacity, int available, List<SeatDTO> seats) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.basePrice = basePrice;
        this.capacity = capacity;
        this.available = available;
        this.seats = seats;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public ZoneType getType() { return type; }
    public double getBasePrice() { return basePrice; }
    public int getCapacity() { return capacity; }
    public int getAvailable() { return available; }
    public List<SeatDTO> getSeats() { return seats; }
}
