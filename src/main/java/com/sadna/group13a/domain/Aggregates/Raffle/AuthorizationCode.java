package com.sadna.group13a.domain.Aggregates.Raffle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "authorization_codes")
public class AuthorizationCode
{
    @Id
    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "expiration_time", nullable = false)
    private LocalDateTime expirationTime;

    /** Required by JPA. Do not use in business code. */
    protected AuthorizationCode() {}

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
