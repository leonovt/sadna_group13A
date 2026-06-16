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
 *
 * The Jackson @JsonTypeInfo / @JsonSubTypes annotations enable the
 * DiscountPolicyConverter to serialize/deserialize recursive trees as JSON.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = NoDiscountPolicy.class,       name = "NO_DISCOUNT"),
    @JsonSubTypes.Type(value = SimpleDiscount.class,         name = "SIMPLE"),
    @JsonSubTypes.Type(value = ConditionalDiscount.class,    name = "CONDITIONAL"),
    @JsonSubTypes.Type(value = CouponDiscount.class,         name = "COUPON"),
    @JsonSubTypes.Type(value = AdditiveDiscountPolicy.class, name = "ADDITIVE"),
    @JsonSubTypes.Type(value = MaxDiscountPolicy.class,      name = "MAX")
})
public interface DiscountPolicy {
    double calculateDiscount(double basePrice, DiscountContext ctx);
}
