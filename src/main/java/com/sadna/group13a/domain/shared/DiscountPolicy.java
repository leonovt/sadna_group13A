package com.sadna.group13a.domain.shared;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.sadna.group13a.domain.policies.discount.AdditiveDiscountPolicy;
import com.sadna.group13a.domain.policies.discount.ConditionalDiscount;
import com.sadna.group13a.domain.policies.discount.CouponDiscount;
import com.sadna.group13a.domain.policies.discount.MaxDiscountPolicy;
import com.sadna.group13a.domain.policies.discount.NoDiscountPolicy;
import com.sadna.group13a.domain.policies.discount.SimpleDiscount;

/**
 * Composite-pattern component for discount rules.
 * Leaf implementations compute a single discount amount (simple %, conditional, coupon).
 * Composite implementations (AdditiveDiscountPolicy, MaxDiscountPolicy) combine children.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "policyClass")
@JsonSubTypes({
        @JsonSubTypes.Type(NoDiscountPolicy.class),
        @JsonSubTypes.Type(SimpleDiscount.class),
        @JsonSubTypes.Type(ConditionalDiscount.class),
        @JsonSubTypes.Type(CouponDiscount.class),
        @JsonSubTypes.Type(MaxDiscountPolicy.class),
        @JsonSubTypes.Type(AdditiveDiscountPolicy.class)
})
public interface DiscountPolicy {
    double calculateDiscount(double basePrice, DiscountContext ctx);
}
