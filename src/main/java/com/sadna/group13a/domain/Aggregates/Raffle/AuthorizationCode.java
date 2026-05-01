package com.sadna.group13a.domain.Aggregates.Raffle;

import java.time.LocalDateTime;
import java.util.UUID;

public class AuthorizationCode
{
    private final String code;
    private final String userId;
    private final String eventId;
    private final LocalDateTime expirationTime;

    public AuthorizationCode(String userId, String eventId, int validForMinutes) {
        this.code = UUID.randomUUID().toString(); // Generates the unique code
        this.userId = userId;
        this.eventId = eventId;
        this.expirationTime = LocalDateTime.now().plusMinutes(validForMinutes);
    }

    /**
     * Domain Logic: Checks if the code is valid for a specific user and hasn't expired.
     */
    public boolean isValidFor(String checkUserId, String checkEventId)
    {
        if (!this.userId.equals(checkUserId) || !this.eventId.equals(checkEventId)) {
            return false;
        }
        return LocalDateTime.now().isBefore(expirationTime);
    }
    
    // --- Getters ---
    public String getCode() { return code; }
    public String getUserId() { return userId; }
    public String getEventId() { return eventId; }
    public LocalDateTime getExpirationTime() { return expirationTime; }

}
