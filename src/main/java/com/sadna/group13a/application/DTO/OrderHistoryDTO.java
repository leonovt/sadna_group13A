package com.sadna.group13a.application.DTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Data Transfer Object for a finalized purchase receipt (OrderHistory).
 * Maps from the domain {@code OrderHistory} via Jackson; unmapped domain fields
 * (e.g. the internal payment transactionId) are intentionally ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderHistoryDTO(
    String receiptId,
    String userId,
    LocalDateTime purchaseDate,
    double totalPaid,
    List<OrderHistoryItemDTO> items
) {}
