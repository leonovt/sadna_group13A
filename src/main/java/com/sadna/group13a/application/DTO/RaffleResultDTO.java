package com.sadna.group13a.application.DTO;

/**
 * Output DTO: Sent back to the Producer after a successful draw.
 */
public record RaffleResultDTO(
    String raffleId,
    String message,
    int expectedWinnersDrawn
) {}
