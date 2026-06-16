package com.sadna.group13a.presentation.views.company;

import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.EventService;
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
public class EventPoliciesPresenter {

    private final EventService eventService;

    public EventPoliciesPresenter(EventService eventService) {
        this.eventService = eventService;
    }

    private String getToken() {
        return (String) VaadinSession.getCurrent().getAttribute("token");
    }

    public void handleSetPurchasePolicy(String eventId,
                                        List<PurchasePolicy> rules,
                                        String mode,
                                        EventPoliciesView view) {
        String token = getToken();
        if (token == null) { UI.getCurrent().navigate("login"); return; }
        PurchasePolicy policy = buildPurchasePolicy(rules, mode);
        Result<Void> result = eventService.setPurchasePolicy(token, eventId, policy);
        if (result.isSuccess()) {
            view.showSuccess("Purchase policy applied to event.");
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handleSetDiscountPolicy(String eventId,
                                        List<DiscountPolicy> discounts,
                                        String mode,
                                        EventPoliciesView view) {
        String token = getToken();
        if (token == null) { UI.getCurrent().navigate("login"); return; }
        DiscountPolicy policy = buildDiscountPolicy(discounts, mode);
        Result<Void> result = eventService.setDiscountPolicy(token, eventId, policy);
        if (result.isSuccess()) {
            view.showSuccess("Discount policy applied to event.");
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

    public void handleLoadCurrentPolicies(String eventId, EventPoliciesView view) {
        String token = getToken();
        if (token == null) return;
        String purchaseDesc = eventService.getPurchasePolicyDescription(token, eventId)
                .getData().orElse("—");
        String discountDesc = eventService.getDiscountPolicyDescription(token, eventId)
                .getData().orElse("—");
        view.showCurrentPolicies(purchaseDesc, discountDesc);
    }

    public void handleBack(String companyId) {
        UI.getCurrent().navigate("company/" + companyId + "/events");
    }
}
