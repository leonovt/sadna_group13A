package com.sadna.group13a.infrastructure;

import java.time.LocalDateTime;
import java.util.UUID;

public record PendingNotification(
    String id,
    String userId,
    String message,
    LocalDateTime createdAt
) {
    public static PendingNotification of(String userId, String message) {
        return new PendingNotification(UUID.randomUUID().toString(), userId, message, LocalDateTime.now());
    }
}
