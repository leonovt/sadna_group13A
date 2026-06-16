package com.sadna.group13a.domain.Aggregates.Raffle;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

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
     * Reconstructs a previously-issued code exactly as persisted. Deliberately separate
     * from the (userId, eventId, validForMinutes) constructor above, which always mints a
     * fresh random code and a new expiration window — using it for deserialization would
     * silently discard the real persisted code and expiry.
     */
    @JsonCreator
    public AuthorizationCode(@JsonProperty("code") String code, @JsonProperty("userId") String userId,
                              @JsonProperty("eventId") String eventId,
                              @JsonProperty("expirationTime") LocalDateTime expirationTime) {
        this.code = code;
        this.userId = userId;
        this.eventId = eventId;
        this.expirationTime = expirationTime;
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
