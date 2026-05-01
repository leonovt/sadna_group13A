package com.sadna.group13a.domain.Aggregates.Event;

<<<<<<< HEAD
import com.sadna.group13a.domain.shared.SeatStatus;
import com.sadna.group13a.domain.shared.SeatUnavailableException;

=======
>>>>>>> origin/feature/application-layer
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

<<<<<<< HEAD
=======
import com.sadna.group13a.domain.shared.SeatUnavailableException;

>>>>>>> origin/feature/application-layer
/**
 * Entity within the Event aggregate — represents a single numbered seat
 * in a SEATED zone.
 *
 * Implements the 10-minute hold mechanism:
 * <ol>
 *   <li>{@code hold(userId)} — transitions AVAILABLE → HELD with a TTL</li>
 *   <li>{@code release()} — manually releases the hold (user cancelled)</li>
 *   <li>{@code sell()} — transitions HELD → SOLD (payment completed)</li>
 *   <li>Lazy expiry — a HELD seat whose {@code holdExpiresAt} is in the past
 *       is treated as AVAILABLE by {@link #getEffectiveStatus()}</li>
 * </ol>
 *
 * From UML: Zone → Seat composition.
 */
public class Seat {

    /** Default hold duration. */
    public static final Duration DEFAULT_HOLD_DURATION = Duration.ofMinutes(10);

    private final String id;
    private final String label;  // e.g. "A-12", "B-5"
    private SeatStatus status;
    private String heldByUserId;
    private Instant holdExpiresAt;

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

    /**
     * Returns the raw persisted status (may be stale if hold expired).
     */
    public synchronized SeatStatus getStatus() {
        return status;
    }

    /**
     * Returns the effective status applying lazy expiry.
     * If the seat is HELD but the hold has expired, returns AVAILABLE.
     */
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

    /**
     * Attempts to hold this seat for the given user for the default duration.
     *
     * @param userId the user requesting the hold
     * @throws SeatUnavailableException if the seat is currently held or sold
     */
    public synchronized void hold(String userId) {
        hold(userId, DEFAULT_HOLD_DURATION);
    }

    /**
     * Attempts to hold this seat for the given user for a custom duration.
     *
     * @param userId   the user requesting the hold
     * @param duration how long the hold should last
     * @throws SeatUnavailableException if the seat is currently held or sold
     */
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

        // AVAILABLE → HELD
        this.status = SeatStatus.HELD;
        this.heldByUserId = userId;
        this.holdExpiresAt = Instant.now().plus(duration);
    }

    /**
     * Manually releases the hold, returning the seat to AVAILABLE.
     * No-op if the seat is already AVAILABLE (or hold expired).
     *
     * @throws SeatUnavailableException if the seat is SOLD (cannot un-sell)
     */
    public synchronized void release() {
        if (status == SeatStatus.SOLD) {
            throw new SeatUnavailableException(id, "cannot release a sold seat");
        }
        clearHold();
    }

    /**
     * Completes the purchase — transitions from HELD → SOLD.
     *
     * @param userId the user completing the purchase (must match holder)
     * @throws SeatUnavailableException if the seat is not held, or held by another user, or hold expired
     */
    public synchronized void sell(String userId) {
        if (getEffectiveStatus() != SeatStatus.HELD) {
            throw new SeatUnavailableException(id, "seat is not currently held");
        }
        if (!userId.equals(heldByUserId)) {
            throw new SeatUnavailableException(id, "seat is held by a different user");
        }
        this.status = SeatStatus.SOLD;
        // keep heldByUserId as the buyer reference
        this.holdExpiresAt = null;  // no longer relevant
    }

    // ── Internal ──────────────────────────────────────────────────

    private void clearHold() {
        this.status = SeatStatus.AVAILABLE;
        this.heldByUserId = null;
        this.holdExpiresAt = null;
    }
<<<<<<< HEAD
}
=======
}
>>>>>>> origin/feature/application-layer
