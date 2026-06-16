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
 *
 * The Jackson @JsonTypeInfo / @JsonSubTypes annotations enable the
 * PurchasePolicyConverter to serialize/deserialize recursive trees as JSON
 * without changing the field types in ProductionCompany or Event.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = AllowAllPolicy.class,       name = "ALLOW_ALL"),
    @JsonSubTypes.Type(value = AgeRestrictionPolicy.class, name = "AGE_RESTRICTION"),
    @JsonSubTypes.Type(value = MinTicketsPolicy.class,     name = "MIN_TICKETS"),
    @JsonSubTypes.Type(value = MaxTicketsPolicy.class,     name = "MAX_TICKETS"),
    @JsonSubTypes.Type(value = AndPolicy.class,            name = "AND"),
    @JsonSubTypes.Type(value = OrPolicy.class,             name = "OR")
})
public interface PurchasePolicy {
    boolean isSatisfied(PurchaseContext ctx);
}
