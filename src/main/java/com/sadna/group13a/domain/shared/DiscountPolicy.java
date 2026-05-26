package com.sadna.group13a.domain.shared;

/**
 * Composite-pattern component for discount rules.
 * Leaf implementations compute a single discount amount (simple %, conditional, coupon).
 * Composite implementations (AdditiveDiscountPolicy, MaxDiscountPolicy) combine children.
 */
public interface DiscountPolicy {
    double calculateDiscount(double basePrice, DiscountContext ctx);
}
