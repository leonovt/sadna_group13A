package com.sadna.group13a.domain.policy;

/**
 * Abstract interface for a Purchase Policy.
 * Evaluates whether a cart/order is valid for purchase.
 */
public interface PurchasePolicy {
    
    /**
     * @return true if the policy is satisfied, false otherwise
     */
    boolean isSatisfied(); // Simplified for V1
}
