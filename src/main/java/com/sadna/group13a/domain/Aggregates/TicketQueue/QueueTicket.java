package com.sadna.group13a.domain.Aggregates.TicketQueue;

import java.time.LocalDateTime;

/**
 * Represents a single user's position in a virtual waiting queue.
 * Transitions from "waiting" to "active" when access is granted via grantAccess().
 */
public class QueueTicket {

    private final String userId;
    private int positionInLine;
    private LocalDateTime grantedAt;
    private LocalDateTime expiresAt;

    public QueueTicket(String userId, int positionInLine) {
        this.userId = userId;
        this.positionInLine = positionInLine;
    }

    /**
     * Marks this ticket as granted, setting the expiry window.
     * Called by TicketQueue when the user is admitted from the waiting list.
     */
    public void grantAccess(int validMinutes) {
        this.grantedAt = LocalDateTime.now();
        this.expiresAt = grantedAt.plusMinutes(validMinutes);
    }

    public void decrementPosition(int amount) {
        this.positionInLine = Math.max(1, this.positionInLine - amount);
    }

    public boolean hasAccess() {
        return grantedAt != null && LocalDateTime.now().isBefore(expiresAt);
    }

    public String getUserId() { return userId; }
    public int getPositionInLine() { return positionInLine; }
    public LocalDateTime getGrantedAt() { return grantedAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
}
