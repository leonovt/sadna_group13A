package com.sadna.group13a.domain.Events;

public record UserReactivatedEvent(
    String userId,
    String adminId
) {}
