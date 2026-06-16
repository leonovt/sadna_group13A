package com.sadna.group13a.domain.policies.discount;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sadna.group13a.domain.shared.DiscountContext;
import com.sadna.group13a.domain.shared.DiscountPolicy;
import com.sadna.group13a.domain.shared.DomainException;

/**
 * Conditional discount: percentage off applied only when the buyer purchases
 * at least minTickets tickets in a single transaction.
 * percentage is expressed as a fraction in [0.0, 1.0] (e.g. 0.10 = 10% off).
 */
public class ConditionalDiscount implements DiscountPolicy {

    private final double percentage;
    private final int minTickets;

    @JsonCreator
    public ConditionalDiscount(@JsonProperty("percentage") double percentage, @JsonProperty("minTickets") int minTickets) {
        if (percentage < 0.0 || percentage > 1.0)
            throw new DomainException("Discount percentage must be between 0 and 1");
        if (minTickets <= 0)
            throw new DomainException("Illogical rule: minimum tickets must be > 0");
        this.percentage  = percentage;
        this.minTickets  = minTickets;
    }

    public double getPercentage() { return percentage; }
    public int getMinTickets()    { return minTickets; }

    @Override
    public double calculateDiscount(double basePrice, DiscountContext ctx) {
        if (ctx.ticketCount() < minTickets) return 0.0;
        return basePrice * percentage;
    }
}
