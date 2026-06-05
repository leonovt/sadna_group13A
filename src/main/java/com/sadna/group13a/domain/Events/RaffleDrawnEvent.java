package com.sadna.group13a.domain.Events;

import java.util.List;

public record RaffleDrawnEvent(
    String raffleId,
    String eventId,
    int winnerCount,
    List<String> participantUserIds
) {}
