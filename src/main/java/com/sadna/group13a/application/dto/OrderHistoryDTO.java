package com.sadna.group13a.application.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Data Transfer Object for a finalized purchase receipt (OrderHistory).
 */
public record OrderHistoryDTO(
    String receiptId,
    String userId,
    LocalDateTime purchaseDate,
    double totalPaid,
    List<OrderHistoryItemDTO> items
) {}
