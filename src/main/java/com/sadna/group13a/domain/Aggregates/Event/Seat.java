package com.sadna.group13a.domain.Aggregates.Event;

import com.sadna.group13a.domain.shared.SeatUnavailableException;
import jakarta.persistence.*;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "seats")
public class Seat {

    public static final Duration DEFAULT_HOLD_DURATION = Duration.ofMinutes(10);

    @Id
    private String id;

    @Column(nullable = false)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatStatus status;

    @Column(name = "held_by_user_id")
    private String heldByUserId;

    @Column(name = "hold_expires_at")
    private Instant holdExpiresAt;

    protected Seat() {}

    public Seat(String id, String label) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Seat id cannot be null or blank");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Seat label cannot be null or blank");
        }
        this.id = id;
        this.label = label;
        this.status = SeatStatus.AVAILABLE;
        this.heldByUserId = null;
        this.holdExpiresAt = null;
    }

    public Seat(String label) {
        this(UUID.randomUUID().toString(), label);
    }

    // ── Identity ──────────────────────────────────────────────────

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    // ── Status ────────────────────────────────────────────────────

    public synchronized SeatStatus getStatus() {
        return status;
    }

    public synchronized SeatStatus getEffectiveStatus() {
        if (status == SeatStatus.HELD && holdExpiresAt != null
                && Instant.now().isAfter(holdExpiresAt)) {
            return SeatStatus.AVAILABLE;
        }
        return status;
    }

    public synchronized String getHeldByUserId() {
        return heldByUserId;
    }

    public synchronized Instant getHoldExpiresAt() {
        return holdExpiresAt;
    }

    // ── Hold / Release / Sell ─────────────────────────────────────

    public synchronized void hold(String userId) {
        hold(userId, DEFAULT_HOLD_DURATION);
    }

    public synchronized void hold(String userId, Duration duration) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId cannot be null or blank");
        }
        if (duration == null || duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("Hold duration must be positive");
        }

        SeatStatus effective = getEffectiveStatus();

        if (effective == SeatStatus.SOLD) {
            throw new SeatUnavailableException(id, "seat is already sold");
        }
        if (effective == SeatStatus.HELD) {
            throw new SeatUnavailableException(id, "seat is currently held by another user");
        }

        this.status = SeatStatus.HELD;
        this.heldByUserId = userId;
        this.holdExpiresAt = Instant.now().plus(duration);
    }

    public synchronized void release() {
        if (status == SeatStatus.SOLD) {
            throw new SeatUnavailableException(id, "cannot release a sold seat");
        }
        clearHold();
    }

    public synchronized void sell(String userId) {
        if (getEffectiveStatus() != SeatStatus.HELD) {
            throw new SeatUnavailableException(id, "seat is not currently held");
        }
        if (!userId.equals(heldByUserId)) {
            throw new SeatUnavailableException(id, "seat is held by a different user");
        }
        this.status = SeatStatus.SOLD;
        this.holdExpiresAt = null;
    }

    public synchronized void unsell() {
        if (status != SeatStatus.SOLD) return;
        clearHold();
    }

    // ── Internal ──────────────────────────────────────────────────

    private void clearHold() {
        this.status = SeatStatus.AVAILABLE;
        this.heldByUserId = null;
        this.holdExpiresAt = null;
    }
}
