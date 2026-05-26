package com.sadna.group13a.domain.shared;

/**
 * Composite-pattern component for purchase rules.
 * Leaf implementations check a single rule (age, ticket count, etc.).
 * Composite implementations (AndPolicy, OrPolicy) combine two children.
 */
public interface PurchasePolicy {
    boolean isSatisfied(PurchaseContext ctx);
}
