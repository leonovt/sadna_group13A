package com.sadna.group13a.domain.shared;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.sadna.group13a.domain.policies.purchase.AgeRestrictionPolicy;
import com.sadna.group13a.domain.policies.purchase.AllowAllPolicy;
import com.sadna.group13a.domain.policies.purchase.AndPolicy;
import com.sadna.group13a.domain.policies.purchase.MaxTicketsPolicy;
import com.sadna.group13a.domain.policies.purchase.MinTicketsPolicy;
import com.sadna.group13a.domain.policies.purchase.OrPolicy;

/**
 * Composite-pattern component for purchase rules.
 * Leaf implementations check a single rule (age, ticket count, etc.).
 * Composite implementations (AndPolicy, OrPolicy) combine two children.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "policyClass")
@JsonSubTypes({
        @JsonSubTypes.Type(AllowAllPolicy.class),
        @JsonSubTypes.Type(AgeRestrictionPolicy.class),
        @JsonSubTypes.Type(MaxTicketsPolicy.class),
        @JsonSubTypes.Type(MinTicketsPolicy.class),
        @JsonSubTypes.Type(AndPolicy.class),
        @JsonSubTypes.Type(OrPolicy.class)
})
public interface PurchasePolicy {
    boolean isSatisfied(PurchaseContext ctx);

    /** Returns a human-readable description of every rule that failed for the given context.
     *  Returns an empty list when the policy is satisfied. */
    default java.util.List<String> getFailureReasons(PurchaseContext ctx) {
        return isSatisfied(ctx) ? java.util.List.of() : java.util.List.of("Purchase policy not satisfied.");
    }
}
