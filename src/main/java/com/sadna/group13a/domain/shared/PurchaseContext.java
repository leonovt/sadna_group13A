package com.sadna.group13a.domain.shared;

/**
 * Immutable snapshot of the buyer's context at checkout time.
 * Passed into every PurchasePolicy.isSatisfied() call so rules can inspect
 * who is buying, how many tickets, their age, and any coupon code entered.
 *
 * userAge defaults to 0 when the system does not yet store user age — age-based
 * policies will correctly block until age data is populated.
 */
public record PurchaseContext(
        String userId,
        int ticketCount,
        int userAge,
        String couponCode
) {}
