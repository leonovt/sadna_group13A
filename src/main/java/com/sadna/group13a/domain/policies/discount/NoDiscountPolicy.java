package com.sadna.group13a.domain.policies.discount;

import com.sadna.group13a.domain.shared.DiscountContext;
import com.sadna.group13a.domain.shared.DiscountPolicy;

/** Default leaf — applies no discount. Assigned to every new Company and Event. */
public class NoDiscountPolicy implements DiscountPolicy {

    @Override
    public double calculateDiscount(double basePrice, DiscountContext ctx) {
        return 0.0;
    }
}
