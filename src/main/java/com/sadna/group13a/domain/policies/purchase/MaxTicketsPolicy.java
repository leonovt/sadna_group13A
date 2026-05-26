package com.sadna.group13a.domain.policies.purchase;

import com.sadna.group13a.domain.shared.DomainException;
import com.sadna.group13a.domain.shared.PurchaseContext;
import com.sadna.group13a.domain.shared.PurchasePolicy;

/** Blocks purchase when the ticket count in the order exceeds the configured maximum. */
public class MaxTicketsPolicy implements PurchasePolicy {

    private final int max;

    public MaxTicketsPolicy(int max) {
        if (max <= 0) throw new DomainException("Illogical rule: max tickets must be > 0");
        this.max = max;
    }

    public int getMax() { return max; }

    @Override
    public boolean isSatisfied(PurchaseContext ctx) {
        return ctx.ticketCount() <= max;
    }
}
