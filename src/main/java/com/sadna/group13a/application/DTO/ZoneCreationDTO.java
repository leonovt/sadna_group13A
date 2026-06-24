package com.sadna.group13a.application.DTO;

import com.sadna.group13a.domain.Aggregates.Event.ZoneType;

/**
 * Input carrier describing a single zone the organiser wants to add to a new
 * venue map. Holds primitives only — the application layer translates these
 * specs into the domain {@code Zone}/{@code Seat} aggregate, so the
 * presentation layer never constructs domain entities directly.
 *
 * <p>{@code capacity} is the number of seats to generate for a
 * {@link ZoneType#SEATED} zone, or the maximum standing capacity for a
 * {@link ZoneType#STANDING} zone.</p>
 */
public record ZoneCreationDTO(String name, ZoneType type, double basePrice, int capacity, int rows, int columns) {
}
