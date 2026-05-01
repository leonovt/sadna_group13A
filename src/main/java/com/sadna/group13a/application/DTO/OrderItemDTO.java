package com.sadna.group13a.application.DTO;

/**
 * Data Transfer Object for an item currently in a cart (ActiveOrder).
 */
public record OrderItemDTO(
    String eventId,
    String zoneId,
    String seatId,
    double basePrice
) {}
