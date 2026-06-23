package com.sadna.group13a.infrastructure;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserNotification(
    String id,
    String userId,
    String message,
    String type,
    String metadata,
    LocalDateTime createdAt
) {
    public static final String TYPE_GENERAL = "GENERAL";
    public static final String TYPE_STAFF_NOMINATION = "STAFF_NOMINATION";

    public static UserNotification general(String userId, String message) {
        return new UserNotification(UUID.randomUUID().toString(), userId,
                message, TYPE_GENERAL, null, LocalDateTime.now());
    }

    public static UserNotification nomination(String userId, String message, String companyId) {
        return new UserNotification(UUID.randomUUID().toString(), userId,
                message, TYPE_STAFF_NOMINATION, companyId, LocalDateTime.now());
    }
}
