package com.sadna.group13a.domain.Events;

public record UserBannedEvent(
    String targetUserId,
    String adminId
) {}
