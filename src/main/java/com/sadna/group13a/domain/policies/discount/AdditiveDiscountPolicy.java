package com.sadna.group13a.domain.policies.discount;

import com.sadna.group13a.domain.shared.DiscountContext;
import com.sadna.group13a.domain.shared.DiscountPolicy;

import java.util.List;

/**
 * Composite — כפל הנחות (stacking): the discount returned is the sum of all children's discounts.
 * Use this when multiple promotions should accumulate.
 */
public class AdditiveDiscountPolicy implements DiscountPolicy {

    private final List<DiscountPolicy> children;

    public AdditiveDiscountPolicy(List<DiscountPolicy> children) {
        if (children == null || children.isEmpty())
            throw new IllegalArgumentException("Additive discount policy must have at least one child");
        this.children = List.copyOf(children);
    }

    public List<DiscountPolicy> getChildren() { return children; }

    @Override
    public double calculateDiscount(double basePrice, DiscountContext ctx) {
        return children.stream()
                .mapToDouble(d -> d.calculateDiscount(basePrice, ctx))
                .sum();
    }
}
