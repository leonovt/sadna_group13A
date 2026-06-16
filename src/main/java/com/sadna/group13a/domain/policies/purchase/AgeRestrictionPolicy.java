package com.sadna.group13a.domain.policies.purchase;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sadna.group13a.domain.shared.DomainException;
import com.sadna.group13a.domain.shared.PurchaseContext;
import com.sadna.group13a.domain.shared.PurchasePolicy;

/** Blocks purchase when the buyer's age is below the configured minimum. */
public class AgeRestrictionPolicy implements PurchasePolicy {

    private final int minAge;

    @JsonCreator
    public AgeRestrictionPolicy(@JsonProperty("minAge") int minAge) {
        if (minAge < 0) throw new DomainException("Illogical rule: minimum age must be non-negative");
        this.minAge = minAge;
    }

    public int getMinAge() { return minAge; }

    @Override
    public boolean isSatisfied(PurchaseContext ctx) {
        return ctx.userAge() >= minAge;
    }
}
