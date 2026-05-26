package com.sadna.group13a.application.DTO;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Carries suspension details for requirement 11.6.9 (view user suspensions).
 * duration and endDate are null when the suspension is permanent.
 */
public record SuspensionDTO(
        String username,
        LocalDateTime startDate,
        Duration duration,
        LocalDateTime endDate
) {}
