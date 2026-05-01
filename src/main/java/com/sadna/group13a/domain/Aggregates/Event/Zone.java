package com.sadna.group13a.domain.Aggregates.Event;

import com.sadna.group13a.domain.shared.ZoneType;

/**
 * Abstract entity within the Event aggregate — represents a zone in a venue.
 *
 * Subclasses handle specific logic for SEATED vs STANDING capacity.
 */
public abstract class Zone {

    private final String id;
    private final String name;
    private final ZoneType type;
    private final double basePrice;

    protected Zone(String id, String name, ZoneType type, double basePrice) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Zone id cannot be null or blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Zone name cannot be null or blank");
        }
        if (basePrice < 0) {
            throw new IllegalArgumentException("Base price cannot be negative");
        }
        this.id = id;
        this.name = name;
        this.type = type;
        this.basePrice = basePrice;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public ZoneType getType() {
        return type;
    }

    public double getBasePrice() {
        return basePrice;
    }

    public abstract int getMaxCapacity();

    public abstract int getAvailableSeatCount();

    public abstract int getActiveHoldCount();

    public abstract int getSoldCount();
}