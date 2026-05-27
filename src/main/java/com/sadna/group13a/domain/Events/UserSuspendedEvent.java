package com.sadna.group13a.domain.Events;

import java.time.LocalDateTime;

/**
 * Published when a System Admin suspends a user (UC 11.6.7).
 *
 * Distinct from {@link UserBannedEvent}: a suspension only restricts the user to
 * view-only access for a finite (or indefinite) period and must NOT revoke their
 * company roles. {@code suspendedUntil} is {@code null} for a permanent suspension.
 */
public record UserSuspendedEvent(
    String targetUserId,
    String adminId,
    LocalDateTime suspendedUntil
) {}
