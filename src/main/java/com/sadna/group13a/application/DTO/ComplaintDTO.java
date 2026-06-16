package com.sadna.group13a.application.DTO;

import java.time.LocalDateTime;

/**
 * Read model for a complaint (II.3.3 / II.6.3). Carries the complainant's username for
 * display; the underlying user id is not exposed (SL-2).
 */
public record ComplaintDTO(
        String id,
        String complainantUsername,
        String subject,
        String message,
        LocalDateTime createdAt,
        String status,
        String adminResponse,
        LocalDateTime resolvedAt) {
}
