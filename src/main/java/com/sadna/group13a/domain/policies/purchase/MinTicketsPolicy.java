package com.sadna.group13a.domain.policies.purchase;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sadna.group13a.domain.shared.DomainException;
import com.sadna.group13a.domain.shared.PurchaseContext;
import com.sadna.group13a.domain.shared.PurchasePolicy;

/** Blocks purchase when the ticket count in the order is below the configured minimum. */
public class MinTicketsPolicy implements PurchasePolicy {

    private final int min;

    @JsonCreator
    public MinTicketsPolicy(@JsonProperty("min") int min) {
        if (min <= 0) throw new DomainException("Illogical rule: min tickets must be > 0");
        this.min = min;
    }

    public int getMin() { return min; }

    @Override
    public boolean isSatisfied(PurchaseContext ctx) {
        return ctx.ticketCount() >= min;
    }
}
