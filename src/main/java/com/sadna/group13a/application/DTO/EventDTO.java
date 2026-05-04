package com.sadna.group13a.application.DTO;

import java.time.LocalDateTime;

/**
 * Data Transfer Object for an Event.
 */
public record EventDTO(
    String id,
    String title,
    String description,
    String companyId,
    LocalDateTime eventDate,
    String category,
    boolean isPublished,
    int totalAvailableTickets
) {}
