package com.sadna.group13a.domain.policies.purchase;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sadna.group13a.domain.shared.PurchaseContext;
import com.sadna.group13a.domain.shared.PurchasePolicy;

import java.util.List;

/** Composite — at least one child must be satisfied (logical OR). */
public class OrPolicy implements PurchasePolicy {

    private final List<PurchasePolicy> children;

    public OrPolicy(List<PurchasePolicy> children) {
        if (children == null || children.size() < 2)
            throw new IllegalArgumentException("OrPolicy requires at least 2 children");
        if (children.contains(null))
            throw new IllegalArgumentException("Policy children cannot be null");
        this.children = List.copyOf(children);
    }

    @JsonCreator
    public OrPolicy(@JsonProperty("left")  PurchasePolicy left,
                    @JsonProperty("right") PurchasePolicy right) {
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
}
