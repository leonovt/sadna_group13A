package com.sadna.group13a.application.DTO;

import java.time.LocalDateTime;

/**
 * Read model for an inquiry to a company (II.3.7 / II.4.4). Carries the sender's username
 * for display; the underlying user id is not exposed (SL-2).
 */
public record InquiryDTO(
        String id,
        String fromUsername,
        String companyId,
        String message,
        LocalDateTime createdAt,
        String status,
        String response,
        LocalDateTime respondedAt) {
}
