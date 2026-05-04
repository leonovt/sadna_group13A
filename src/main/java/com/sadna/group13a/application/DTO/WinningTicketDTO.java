package com.sadna.group13a.application.DTO;

import java.time.LocalDateTime;

/**
 * Output DTO: Used to securely send an authorization code to a winning user.
 */
public record WinningTicketDTO(
    String eventId,
    String authorizationCode,
    LocalDateTime expiresAt
) {}
