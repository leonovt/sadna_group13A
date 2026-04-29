package com.sadna.group13a.application.dto;

import com.sadna.group13a.domain.shared.SeatStatus;

/**
 * Data Transfer Object for a Seat.
 */
public record SeatDTO(
    String id,
    String label,
    SeatStatus status
) {}
