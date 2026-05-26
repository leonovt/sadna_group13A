package com.sadna.group13a.domain.policies.discount;

import com.sadna.group13a.domain.shared.DiscountContext;
import com.sadna.group13a.domain.shared.DiscountPolicy;
import com.sadna.group13a.domain.shared.DomainException;

import java.time.LocalDate;

/**
 * Visible (simple) discount: a fixed percentage off, active only within a date range.
 * percentage is expressed as a fraction in [0.0, 1.0] (e.g. 0.10 = 10% off).
 */
public class SimpleDiscount implements DiscountPolicy {

    private final double percentage;
    private final LocalDate startDate;
    private final LocalDate endDate;

    public SimpleDiscount(double percentage, LocalDate startDate, LocalDate endDate) {
        if (percentage < 0.0 || percentage > 1.0)
            throw new DomainException("Discount percentage must be between 0 and 1");
        if (startDate == null || endDate == null)
            throw new DomainException("Date range cannot be null");
        if (endDate.isBefore(startDate))
            throw new DomainException("End date cannot be before start date");
        this.percentage = percentage;
        this.startDate  = startDate;
        this.endDate    = endDate;
    }

    public double getPercentage() { return percentage; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate()   { return endDate; }

    @Override
    public double calculateDiscount(double basePrice, DiscountContext ctx) {
        LocalDate today = LocalDate.now();
        if (today.isBefore(startDate) || today.isAfter(endDate)) return 0.0;
        return basePrice * percentage;
    }
}
