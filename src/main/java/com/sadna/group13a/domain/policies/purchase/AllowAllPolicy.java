package com.sadna.group13a.domain.policies.purchase;

import com.sadna.group13a.domain.shared.PurchaseContext;
import com.sadna.group13a.domain.shared.PurchasePolicy;

/** Default leaf — always permits the purchase. Assigned to every new Company and Event. */
public class AllowAllPolicy implements PurchasePolicy {

    @Override
    public boolean isSatisfied(PurchaseContext ctx) {
        return true;
    }
}
