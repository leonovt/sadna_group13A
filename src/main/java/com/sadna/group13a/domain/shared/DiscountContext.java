package com.sadna.group13a.domain.shared;

/**
 * Immutable snapshot of the buyer's context at checkout time, used by discount policies.
 * Passed into every DiscountPolicy.calculateDiscount() call so conditional and coupon
 * discounts can inspect quantity and the code the user entered.
 */
public record DiscountContext(
        String userId,
        int ticketCount,
        String couponCode
) {}
