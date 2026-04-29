package com.sadna.group13a.domain.shared;

/**
 * Lifecycle status of an ActiveOrder (unfulfilled cart).
 */
public enum OrderStatus {
    /** Cart is open and items can be added/removed. */
    OPEN,
    /** Checkout is in progress (payment pending). */
    PENDING_PAYMENT,
    /** Order completed successfully — becomes an OrderHistory record. */
    COMPLETED,
    /** Order was cancelled by the user or expired. */
    CANCELLED
}
