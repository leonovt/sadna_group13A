package com.sadna.group13a.domain.shared;

/**
 * Thrown when a seat is already held or sold and cannot be reserved.
 * This is the core exception for the 10-minute hold mechanism.
 */
public class SeatUnavailableException extends DomainException {

    public SeatUnavailableException(String seatId) {
        super("Seat is not available: " + seatId);
    }

    public SeatUnavailableException(String seatId, String reason) {
        super("Seat is not available: " + seatId + " — " + reason);
    }
}
