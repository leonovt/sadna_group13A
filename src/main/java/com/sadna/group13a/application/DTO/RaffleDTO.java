package com.sadna.group13a.application.DTO;

import com.sadna.group13a.domain.Aggregates.Raffle.RaffleStatus;

/**
 * Output DTO: Used to safely send the general status of a Raffle to the frontend.
 */
public record RaffleDTO(
    String id,
    String eventId,
    String companyId,
    RaffleStatus status,
    int totalParticipants
) {}
