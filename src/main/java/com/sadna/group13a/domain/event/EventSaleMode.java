package com.sadna.group13a.domain.event;

/**
 * Defines how tickets for an event are sold.
 */
public enum EventSaleMode {
    /** General admission/regular sale open to everyone. */
    REGULAR,
    
    /** High-demand event using a pre-registration lottery system. */
    RAFFLE
}
