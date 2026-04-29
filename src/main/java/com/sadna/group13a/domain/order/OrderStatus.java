package com.sadna.group13a.domain.order;

/**
 * Status of an active order (shopping cart).
 */
public enum OrderStatus {
    /** Order is currently being built by the user (Items can be added/removed). */
    DRAFT,
    
    /** Order has been successfully paid for and converted into OrderHistory. */
    COMPLETED,
    
    /** Order was cancelled (e.g., hold timer expired, or payment failed). */
    CANCELLED
}
