package com.sadna.group13a.domain.Events;

import java.time.LocalDateTime;

public record RaffleWonEvent(
    String userId,
    String eventId,
    String authCode,
    LocalDateTime expiresAt
) {}
