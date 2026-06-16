package com.sadna.group13a.application;

import com.sadna.group13a.domain.policies.discount.*;
import com.sadna.group13a.domain.policies.purchase.*;
import com.sadna.group13a.domain.shared.DiscountPolicy;
import com.sadna.group13a.domain.shared.PurchasePolicy;

import java.util.stream.Collectors;

/** Converts policy objects to short human-readable descriptions. */
public final class PolicyFormatter {

    private PolicyFormatter() {}

    public static String describe(PurchasePolicy policy) {
        if (policy == null) return "None";
        if (policy instanceof AllowAllPolicy)          return "Allow All";
        if (policy instanceof AgeRestrictionPolicy p)  return "Age ≥ " + p.getMinAge();
        if (policy instanceof MinTicketsPolicy p)       return "Min tickets: " + p.getMin();
        if (policy instanceof MaxTicketsPolicy p)       return "Max tickets: " + p.getMax();
        if (policy instanceof AndPolicy p) {
            String children = p.getChildren().stream()
                    .map(PolicyFormatter::describe)
                    .collect(Collectors.joining(", "));
            return "All of: [" + children + "]";
        }
        if (policy instanceof OrPolicy p) {
            String children = p.getChildren().stream()
                    .map(PolicyFormatter::describe)
                    .collect(Collectors.joining(", "));
            return "Any of: [" + children + "]";
        }
        return policy.getClass().getSimpleName();
    }

    public static String describe(DiscountPolicy policy) {
        if (policy == null) return "None";
        if (policy instanceof NoDiscountPolicy)         return "No discount";
        if (policy instanceof SimpleDiscount p)
            return String.format("%.0f%% off (%s – %s)",
                    p.getPercentage() * 100, p.getStartDate(), p.getEndDate());
        if (policy instanceof CouponDiscount p)
            return String.format("Coupon '%s': %.0f%% off", p.getCode(), p.getPercentage() * 100);
        if (policy instanceof ConditionalDiscount p)
            return String.format("%.0f%% off when ≥ %d tickets",
                    p.getPercentage() * 100, p.getMinTickets());
        if (policy instanceof AdditiveDiscountPolicy p) {
            String children = p.getChildren().stream()
                    .map(PolicyFormatter::describe)
                    .collect(Collectors.joining(", "));
            return "Sum of: [" + children + "]";
        }
        if (policy instanceof MaxDiscountPolicy p) {
            String children = p.getChildren().stream()
                    .map(PolicyFormatter::describe)
                    .collect(Collectors.joining(", "));
            return "Best of: [" + children + "]";
        }
        return policy.getClass().getSimpleName();
    }
}
