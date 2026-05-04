package com.sadna.group13a.application.DTO;

import java.time.LocalDateTime;
import java.util.List;

import com.sadna.group13a.domain.shared.OrderStatus;

/**
 * Data Transfer Object for an ActiveOrder (cart checkout session).
 */
public record OrderDTO(
    String orderId,
    String userId,
    OrderStatus status,
    LocalDateTime expiresAt,
    double totalBasePrice,
    List<OrderItemDTO> items
) {}
