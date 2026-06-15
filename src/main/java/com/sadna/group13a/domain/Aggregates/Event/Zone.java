package com.sadna.group13a.domain.Aggregates.Event;

import jakarta.persistence.*;

@Entity
@Table(name = "zones")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "zone_type", discriminatorType = DiscriminatorType.STRING)
public abstract class Zone {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ZoneType type;

    @Column(name = "base_price", nullable = false)
    private double basePrice;

    protected Zone() {}

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
