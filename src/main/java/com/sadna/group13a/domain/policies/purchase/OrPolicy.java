package com.sadna.group13a.domain.policies.purchase;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sadna.group13a.domain.shared.PurchaseContext;
import com.sadna.group13a.domain.shared.PurchasePolicy;

import java.util.ArrayList;
import java.util.List;

/** Composite — at least one child must be satisfied (logical OR). */
public class OrPolicy implements PurchasePolicy {

    private final List<PurchasePolicy> children;

    @JsonCreator
    public OrPolicy(@JsonProperty("children") List<PurchasePolicy> children) {
        if (children == null || children.size() < 2)
            throw new IllegalArgumentException("OrPolicy requires at least 2 children");
        if (children.contains(null))
            throw new IllegalArgumentException("Policy children cannot be null");
        this.children = List.copyOf(children);
    }

    public OrPolicy(PurchasePolicy left, PurchasePolicy right) {
        if (left == null || right == null) throw new IllegalArgumentException("Policy children cannot be null");
        this.children = List.of(left, right);
    }

    public PurchasePolicy getLeft()  { return children.get(0); }
    public PurchasePolicy getRight() { return children.get(1); }
    public List<PurchasePolicy> getChildren() { return children; }

    @Override
    public boolean isSatisfied(PurchaseContext ctx) {
        return children.stream().anyMatch(p -> p.isSatisfied(ctx));
    }

    @Override
    public List<String> getFailureReasons(PurchaseContext ctx) {
        if (isSatisfied(ctx)) return List.of();
        List<String> reasons = new ArrayList<>();
        reasons.add("None of the following conditions were met (at least one is required):");
        for (PurchasePolicy child : children) {
            reasons.addAll(child.getFailureReasons(ctx));
        }
        return reasons;
    }
}
