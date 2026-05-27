package com.sadna.group13a.application.DTO;

import com.sadna.group13a.domain.Aggregates.Event.EventSaleMode;
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
    String location,
    boolean isPublished,
    int totalAvailableTickets,
    EventSaleMode saleMode
) {}
