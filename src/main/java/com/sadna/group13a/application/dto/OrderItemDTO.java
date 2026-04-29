package com.sadna.group13a.application.dto;

/**
 * Data Transfer Object for an item currently in a cart (ActiveOrder).
 */
public record OrderItemDTO(
    String eventId,
    String zoneId,
    String seatId,
    double basePrice
) {}
