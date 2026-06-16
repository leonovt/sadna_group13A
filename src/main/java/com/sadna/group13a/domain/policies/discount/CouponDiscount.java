package com.sadna.group13a.domain.policies.discount;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sadna.group13a.domain.shared.DiscountContext;
import com.sadna.group13a.domain.shared.DiscountPolicy;
import com.sadna.group13a.domain.shared.DomainException;

import java.time.LocalDate;

/**
 * Hidden (coupon) discount: percentage off applied only when the buyer supplies
 * the exact matching code at checkout, and today falls within the validity window.
 * startDate / endDate may both be null for an open-ended coupon.
 * percentage is expressed as a fraction in [0.0, 1.0] (e.g. 0.15 = 15% off).
 */
public class CouponDiscount implements DiscountPolicy {

    private final double    percentage;
    private final String    code;
    private final LocalDate startDate;  // null = no lower bound
    private final LocalDate endDate;    // null = no upper bound

    @JsonCreator
    public CouponDiscount(@JsonProperty("percentage") double    percentage,
                          @JsonProperty("code")       String    code,
                          @JsonProperty("startDate")  LocalDate startDate,
                          @JsonProperty("endDate")    LocalDate endDate) {
        if (percentage < 0.0 || percentage > 1.0)
            throw new DomainException("Discount percentage must be between 0 and 1");
        if (code == null || code.isBlank())
            throw new DomainException("Coupon code cannot be blank");
        if (startDate != null && endDate != null && endDate.isBefore(startDate))
            throw new DomainException("End date cannot be before start date");
        this.percentage = percentage;
        this.code       = code;
        this.startDate  = startDate;
        this.endDate    = endDate;
    }

    public double getPercentage() { return percentage; }
    public String getCode()       { return code; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate()   { return endDate; }

    @Override
    public double calculateDiscount(double basePrice, DiscountContext ctx) {
        if (!code.equals(ctx.couponCode())) return 0.0;
        LocalDate today = LocalDate.now();
        if (startDate != null && today.isBefore(startDate)) return 0.0;
        if (endDate   != null && today.isAfter(endDate))    return 0.0;
        return basePrice * percentage;
    }
}
