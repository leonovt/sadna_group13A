package com.sadna.group13a.application.DTO;

/**
 * Input DTO: Catches the request from a user wanting to join a raffle.
 */
public record RaffleRegistrationDTO(
    String raffleId
) {}