package com.sadna.group13a.application.dto;

import com.sadna.group13a.domain.shared.ZoneType;
import java.util.List;

/**
 * Data Transfer Object for a Zone.
 * If type is STANDING, the seats list will be empty.
 */
public record ZoneDTO(
    String id,
    String name,
    ZoneType type,
    double basePrice,
    int maxCapacity,
    int availableCount,
    List<SeatDTO> seats
) {}
