package com.sadna.group13a.domain.Events;

public record AdminMessageEvent(
    String targetUserId,
    String adminId,
    String message
) {}
