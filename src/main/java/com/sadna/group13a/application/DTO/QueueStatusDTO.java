package com.sadna.group13a.application.DTO;

import java.time.LocalDateTime;

public record QueueStatusDTO(
    String eventId,
    String userId,
    boolean isActive,
    int positionInLine,
    int totalWaiting,
    LocalDateTime accessExpiresAt
) {}
