package com.sadna.group13a.application.dto;

import com.sadna.group13a.domain.order.OrderStatus;
import java.time.LocalDateTime;
import java.util.List;

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
