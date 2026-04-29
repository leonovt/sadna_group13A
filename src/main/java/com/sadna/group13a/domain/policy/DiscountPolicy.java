package com.sadna.group13a.domain.policy;

/**
 * Abstract interface for a Discount Policy.
 * Calculates discount amounts.
 */
public interface DiscountPolicy {
    
    /**
     * @param basePrice the original price
     * @return the calculated discount amount to subtract
     */
    double calculateDiscount(double basePrice);
}
