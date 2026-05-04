package com.sadna.group13a.application.DTO;

import java.time.LocalDateTime;

/**
 * Data Transfer Object for a completed purchase history item.
 */
public record OrderHistoryItemDTO(
    String eventId,
    String eventTitle,
    LocalDateTime eventDate,
    String companyName,
    String zoneName,
    String seatLabel,
    double pricePaid
) {}
