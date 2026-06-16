package com.sadna.group13a.domain.policies.purchase;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sadna.group13a.domain.shared.PurchaseContext;
import com.sadna.group13a.domain.shared.PurchasePolicy;

/** Composite — at least one child must be satisfied (logical OR). */
public class OrPolicy implements PurchasePolicy {

    private final PurchasePolicy left;
    private final PurchasePolicy right;

    @JsonCreator
    public OrPolicy(@JsonProperty("left")  PurchasePolicy left,
                    @JsonProperty("right") PurchasePolicy right) {
        if (left == null || right == null) throw new IllegalArgumentException("Policy children cannot be null");
        this.left = left;
        this.right = right;
    }

    public PurchasePolicy getLeft()  { return left; }
    public PurchasePolicy getRight() { return right; }

    @Override
    public boolean isSatisfied(PurchaseContext ctx) {
        return left.isSatisfied(ctx) || right.isSatisfied(ctx);
    }
}
