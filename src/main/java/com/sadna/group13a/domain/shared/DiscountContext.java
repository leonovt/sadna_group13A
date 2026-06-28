package com.sadna.group13a.domain.shared;

import java.util.List;

/**
 * Immutable snapshot of the buyer's context at checkout time, used by discount policies.
 * Passed into every DiscountPolicy.calculateDiscount() call so conditional and coupon
 * discounts can inspect quantity and the codes the user entered.
 */
public record DiscountContext(
        String userId,
        int ticketCount,
        List<String> couponCodes
) {}
