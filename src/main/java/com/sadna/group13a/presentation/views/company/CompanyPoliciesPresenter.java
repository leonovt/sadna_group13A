package com.sadna.group13a.presentation.views.company;

import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.CompanyService;
import com.sadna.group13a.domain.policies.discount.AdditiveDiscountPolicy;
import com.sadna.group13a.domain.policies.discount.MaxDiscountPolicy;
import com.sadna.group13a.domain.policies.discount.NoDiscountPolicy;
import com.sadna.group13a.domain.policies.purchase.AllowAllPolicy;
import com.sadna.group13a.domain.policies.purchase.AndPolicy;
import com.sadna.group13a.domain.policies.purchase.OrPolicy;
import com.sadna.group13a.domain.shared.DiscountPolicy;
import com.sadna.group13a.domain.shared.PurchasePolicy;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CompanyPoliciesPresenter {

    private final CompanyService companyService;

    public CompanyPoliciesPresenter(CompanyService companyService) {
        this.companyService = companyService;
    }

    private String getToken() {
        return (String) VaadinSession.getCurrent().getAttribute("token");
    }

    public void handleSetPurchasePolicy(String companyId,
                                        List<PurchasePolicy> rules,
                                        String mode,
                                        CompanyPoliciesView view) {
        String token = getToken();
        if (token == null) { UI.getCurrent().navigate("login"); return; }
        PurchasePolicy policy = buildPurchasePolicy(rules, mode);
        Result<Void> result = companyService.setPurchasePolicy(token, companyId, policy);
        if (result.isSuccess()) {
            view.showSuccess("Purchase policy applied to company.");
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handleSetDiscountPolicy(String companyId,
                                        List<DiscountPolicy> discounts,
                                        String mode,
                                        CompanyPoliciesView view) {
        String token = getToken();
        if (token == null) { UI.getCurrent().navigate("login"); return; }
        DiscountPolicy policy = buildDiscountPolicy(discounts, mode);
        Result<Void> result = companyService.setDiscountPolicy(token, companyId, policy);
        if (result.isSuccess()) {
            view.showSuccess("Discount policy applied to company.");
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    private PurchasePolicy buildPurchasePolicy(List<PurchasePolicy> rules, String mode) {
        if (rules == null || rules.isEmpty()) return new AllowAllPolicy();
        if (rules.size() == 1) return rules.get(0);
        return "OR".equals(mode) ? new OrPolicy(rules) : new AndPolicy(rules);
    }

    private DiscountPolicy buildDiscountPolicy(List<DiscountPolicy> discounts, String mode) {
        if (discounts == null || discounts.isEmpty()) return new NoDiscountPolicy();
        if (discounts.size() == 1) return discounts.get(0);
        return "BEST".equals(mode)
                ? new MaxDiscountPolicy(discounts)
                : new AdditiveDiscountPolicy(discounts);
    }

    public void handleLoadCurrentPolicies(String companyId, CompanyPoliciesView view) {
        String token = getToken();
        if (token == null) return;
        String purchaseDesc = companyService.getPurchasePolicyDescription(token, companyId)
                .getData().orElse("—");
        String discountDesc = companyService.getDiscountPolicyDescription(token, companyId)
                .getData().orElse("—");
        view.showCurrentPolicies(purchaseDesc, discountDesc);
    }

    public void handleBack(String companyId) {
        UI.getCurrent().navigate("company/" + companyId);
    }
}
