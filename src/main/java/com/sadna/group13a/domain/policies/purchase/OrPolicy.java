package com.sadna.group13a.domain.policies.purchase;

import com.sadna.group13a.domain.shared.PurchaseContext;
import com.sadna.group13a.domain.shared.PurchasePolicy;

/** Composite — at least one child must be satisfied (logical OR). */
public class OrPolicy implements PurchasePolicy {

    private final PurchasePolicy left;
    private final PurchasePolicy right;

    public OrPolicy(PurchasePolicy left, PurchasePolicy right) {
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
