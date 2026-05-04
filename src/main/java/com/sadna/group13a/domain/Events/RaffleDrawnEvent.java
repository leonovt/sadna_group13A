package com.sadna.group13a.domain.Events;

public record RaffleDrawnEvent(
    String raffleId,
    String eventId,
    int winnerCount
) {}
