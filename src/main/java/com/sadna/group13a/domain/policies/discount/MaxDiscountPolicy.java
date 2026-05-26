package com.sadna.group13a.domain.policies.discount;

import com.sadna.group13a.domain.shared.DiscountContext;
import com.sadna.group13a.domain.shared.DiscountPolicy;

import java.util.List;

/**
 * Composite — אין כפל הנחות (no stacking): only the single highest discount among all
 * children applies. Use this to prevent multiple promotions from combining.
 */
public class MaxDiscountPolicy implements DiscountPolicy {

    private final List<DiscountPolicy> children;

    public MaxDiscountPolicy(List<DiscountPolicy> children) {
        if (children == null || children.isEmpty())
            throw new IllegalArgumentException("Max discount policy must have at least one child");
        this.children = List.copyOf(children);
    }

    public List<DiscountPolicy> getChildren() { return children; }

    @Override
    public double calculateDiscount(double basePrice, DiscountContext ctx) {
        return children.stream()
                .mapToDouble(d -> d.calculateDiscount(basePrice, ctx))
                .max()
                .orElse(0.0);
    }
}
