package com.sadna.group13a.domain.Aggregates.TicketQueue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Represents a single user's position in a virtual waiting queue.
 * Transitions from "waiting" to "active" when access is granted via grantAccess().
 */
@Entity
@Table(name = "queue_tickets")
public class QueueTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ticket_id")
    private String ticketId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "position_in_line", nullable = false)
    private int positionInLine;

    @Column(name = "granted_at")
    private LocalDateTime grantedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /** Required by JPA. Do not use in business code. */
    protected QueueTicket() {}

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
