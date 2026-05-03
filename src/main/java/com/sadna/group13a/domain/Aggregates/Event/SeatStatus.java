package com.sadna.group13a.domain.Aggregates.Event;

/**
 * Seat status lifecycle supporting the 10-minute hold mechanism.
 * <ul>
 *   <li>AVAILABLE - seat can be selected</li>
 *   <li>HELD - temporarily reserved for a user (10 min TTL)</li>
 *   <li>SOLD - purchased, no longer available</li>
 * </ul>
 */
public enum SeatStatus {
    AVAILABLE,
    HELD,
    SOLD
}
