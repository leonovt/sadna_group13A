package com.sadna.group13a.domain.Events;

import java.time.LocalDateTime;

public record QueueTurnArrivedEvent(
    String eventId,
    String userId,
    LocalDateTime expiresAt
) {}
