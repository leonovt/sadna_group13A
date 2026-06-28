package com.sadna.group13a.presentation.views.company;

import com.sadna.group13a.domain.policies.discount.*;
import com.sadna.group13a.domain.policies.purchase.*;
import com.sadna.group13a.domain.shared.DiscountPolicy;
import com.sadna.group13a.domain.shared.PurchasePolicy;
import com.sadna.group13a.presentation.views.auth.LoginView;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Route("company/:companyId/event-policies/:eventId")
@PageTitle("Event Purchase & Discount Policies")
public class EventPoliciesView extends VerticalLayout implements BeforeEnterObserver {

    private final EventPoliciesPresenter presenter;
    private String companyId;
    private String eventId;

    private final Span statusMessage = new Span();
    private final Span currentPurchaseSpan = new Span("—");
    private final Span currentDiscountSpan = new Span("—");

    // ── Purchase policy builder state ─────────────────────────────
    private final List<PurchasePolicy> purchaseRules    = new ArrayList<>();
    private final List<String>         purchaseLabels   = new ArrayList<>();
    private final VerticalLayout       purchaseRuleList = new VerticalLayout();
    private final Select<String>       purchaseModeSelect = new Select<>();

    // ── Discount policy builder state ─────────────────────────────
    private final List<DiscountPolicy> discountRules    = new ArrayList<>();
    private final List<String>         discountLabels   = new ArrayList<>();
    private final VerticalLayout       discountRuleList = new VerticalLayout();
    private final Select<String>       discountModeSelect = new Select<>();

    public EventPoliciesView(EventPoliciesPresenter presenter) {
        this.presenter = presenter;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        companyId = event.getRouteParameters().get("companyId").orElse("");
        eventId   = event.getRouteParameters().get("eventId").orElse("");
        String token = (String) com.vaadin.flow.server.VaadinSession.getCurrent().getAttribute("token");
        if (token == null) { event.forwardTo(LoginView.class); return; }
        initView();
        presenter.handleLoadCurrentPolicies(eventId, this);
    }

    private void initView() {
        removeAll();
        setPadding(true);
        setSpacing(true);

        add(new Button("← Back to Events", e -> presenter.handleBack(companyId)));

        statusMessage.setVisible(false);
        statusMessage.getStyle().set("font-weight", "bold");

        Span subtitle = new Span("Event ID: " + eventId);
        subtitle.getStyle().set("color", "var(--lumo-secondary-text-color)");

        add(new H2("Event Policies"), subtitle, statusMessage);
        add(buildPurchasePolicySection());
        add(buildDiscountPolicySection());
    }

    // ── Purchase Policy Section ───────────────────────────────────

    private VerticalLayout buildPurchasePolicySection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);

        currentPurchaseSpan.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-style", "italic");
        Span currentPurchaseLabel = new Span("Active purchase policy: ");
        currentPurchaseLabel.getStyle().set("font-weight", "bold");

        Select<String> typeSelect = new Select<>();
        typeSelect.setLabel("Rule type");
        typeSelect.setItems("Allow All", "Age Restriction", "Min Tickets", "Max Tickets");
        typeSelect.setWidth("16rem");

        IntegerField paramField = new IntegerField("Parameter");
        paramField.setMin(1);
        paramField.setWidth("10rem");
        paramField.setVisible(false);

        typeSelect.addValueChangeListener(e -> {
            String v = e.getValue();
            boolean needsParam = "Age Restriction".equals(v) || "Min Tickets".equals(v) || "Max Tickets".equals(v);
            paramField.setVisible(needsParam);
            if ("Age Restriction".equals(v)) paramField.setLabel("Minimum age");
            else if ("Min Tickets".equals(v)) paramField.setLabel("Min tickets per order");
            else if ("Max Tickets".equals(v)) paramField.setLabel("Max tickets per order");
        });

        Button addBtn = new Button("+ Add Rule", e -> {
            String type = typeSelect.getValue();
            if (type == null) return;
            PurchasePolicy rule;
            String label;
            switch (type) {
                case "Allow All" -> { rule = new AllowAllPolicy(); label = "Allow All"; }
                case "Age Restriction" -> {
                    int age = paramField.getValue() != null ? paramField.getValue() : 18;
                    rule = new AgeRestrictionPolicy(age); label = "Age ≥ " + age;
                }
                case "Min Tickets" -> {
                    int min = paramField.getValue() != null ? paramField.getValue() : 1;
                    rule = new MinTicketsPolicy(min); label = "Min tickets: " + min;
                }
                case "Max Tickets" -> {
                    int max = paramField.getValue() != null ? paramField.getValue() : 10;
                    rule = new MaxTicketsPolicy(max); label = "Max tickets: " + max;
                }
                default -> { return; }
            }
            purchaseRules.add(rule);
            purchaseLabels.add(label);
            refreshPurchaseList();
            typeSelect.clear();
            paramField.clear();
            paramField.setVisible(false);
        });

        HorizontalLayout addRow = new HorizontalLayout(typeSelect, paramField, addBtn);
        addRow.setAlignItems(Alignment.BASELINE);

        purchaseRuleList.setPadding(false);
        purchaseRuleList.setSpacing(false);

        Button clearBtn = new Button("Clear All", e -> {
            purchaseRules.clear();
            purchaseLabels.clear();
            refreshPurchaseList();
        });
        clearBtn.getStyle().set("color", "var(--lumo-error-color)");

        purchaseModeSelect.setLabel("Combine rules with");
        purchaseModeSelect.setItems("AND (all must pass)", "OR (any passes)");
        purchaseModeSelect.setValue("AND (all must pass)");
        purchaseModeSelect.setWidth("18rem");

        Button applyBtn = new Button("Apply Purchase Policy", e -> {
            String mode = purchaseModeSelect.getValue() != null && purchaseModeSelect.getValue().startsWith("OR") ? "OR" : "AND";
            presenter.handleSetPurchasePolicy(eventId, new ArrayList<>(purchaseRules), mode, this);
        });

        section.add(
            new H3("Purchase Policy"),
            new HorizontalLayout(currentPurchaseLabel, currentPurchaseSpan),
            addRow,
            new Span("Active rules:"), purchaseRuleList, clearBtn,
            new HorizontalLayout(purchaseModeSelect, applyBtn)
        );
        return section;
    }

    private void refreshPurchaseList() {
        purchaseRuleList.removeAll();
        for (int i = 0; i < purchaseLabels.size(); i++) {
            final int idx = i;
            Button removeBtn = new Button("✕", e -> {
                purchaseRules.remove(idx);
                purchaseLabels.remove(idx);
                refreshPurchaseList();
            });
            removeBtn.getStyle().set("color", "var(--lumo-error-color)").set("padding", "0 4px");
            purchaseRuleList.add(new HorizontalLayout(new Span("• " + purchaseLabels.get(i)), removeBtn));
        }
        if (purchaseLabels.isEmpty()) {
            purchaseRuleList.add(new Span("(none — will apply Allow All)"));
        }
    }

    // ── Discount Policy Section ───────────────────────────────────

    private VerticalLayout buildDiscountPolicySection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);

        currentDiscountSpan.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-style", "italic");
        Span currentDiscountLabel = new Span("Active discount policy: ");
        currentDiscountLabel.getStyle().set("font-weight", "bold");

        Select<String> typeSelect = new Select<>();
        typeSelect.setLabel("Discount type");
        typeSelect.setItems("Simple %", "Coupon %", "Conditional %");
        typeSelect.setWidth("16rem");

        NumberField  pctField    = new NumberField("Percentage (0–100)");
        TextField    codeField   = new TextField("Coupon code");
        DatePicker   startPicker = new DatePicker("Start date");
        DatePicker   endPicker   = new DatePicker("End date");
        IntegerField minTickets  = new IntegerField("Min tickets");

        pctField.setMin(0); pctField.setMax(100); pctField.setWidth("10rem");
        codeField.setWidth("12rem");
        minTickets.setMin(1); minTickets.setWidth("10rem");

        VerticalLayout paramBox = new VerticalLayout(pctField);
        paramBox.setPadding(false);
        paramBox.setSpacing(false);
        paramBox.setVisible(false);

        typeSelect.addValueChangeListener(e -> {
            paramBox.removeAll();
            String v = e.getValue();
            if (v == null) { paramBox.setVisible(false); return; }
            paramBox.setVisible(true);
            paramBox.add(pctField);
            if ("Simple %".equals(v)) { paramBox.add(startPicker, endPicker); }
            else if ("Coupon %".equals(v)) { paramBox.add(codeField, startPicker, endPicker); }
            else if ("Conditional %".equals(v)) { paramBox.add(minTickets); }
        });

        Button addBtn = new Button("+ Add Discount", e -> {
            String type = typeSelect.getValue();
            if (type == null) return;
            Double pct = pctField.getValue();
            if (pct == null) pct = 0.0;
            DiscountPolicy discount;
            String label;
            switch (type) {
                case "Simple %" -> {
                    LocalDate s  = startPicker.getValue();
                    LocalDate en = endPicker.getValue();
                    if (s == null || en == null) { showError("Start and end date required for Simple discount."); return; }
                    discount = new SimpleDiscount(pct / 100.0, s, en);
                    label = String.format("Simple: %.0f%% (%s – %s)", pct, s, en);
                }
                case "Coupon %" -> {
                    String code = codeField.getValue();
                    LocalDate s  = startPicker.getValue();
                    LocalDate en = endPicker.getValue();
                    if (code == null || code.isBlank()) { showError("Coupon code required."); return; }
                    if (s == null || en == null) { showError("Start and end date required for Coupon discount."); return; }
                    discount = new CouponDiscount(pct / 100.0, code.trim(), s, en);
                    label = String.format("Coupon '%s': %.0f%% (%s – %s)", code.trim(), pct, s, en);
                }
                case "Conditional %" -> {
                    int min = minTickets.getValue() != null ? minTickets.getValue() : 1;
                    discount = new ConditionalDiscount(pct / 100.0, min);
                    label = String.format("Conditional: %.0f%% when ≥%d tickets", pct, min);
                }
                default -> { return; }
            }
            discountRules.add(discount);
            discountLabels.add(label);
            refreshDiscountList();
            typeSelect.clear();
            pctField.clear();
            codeField.clear();
            startPicker.clear();
            endPicker.clear();
            minTickets.clear();
            paramBox.setVisible(false);
        });

        HorizontalLayout addRow = new HorizontalLayout(typeSelect, addBtn);
        addRow.setAlignItems(Alignment.BASELINE);

        discountRuleList.setPadding(false);
        discountRuleList.setSpacing(false);

        Button clearBtn = new Button("Clear All", e -> {
            discountRules.clear();
            discountLabels.clear();
            refreshDiscountList();
        });
        clearBtn.getStyle().set("color", "var(--lumo-error-color)");

        discountModeSelect.setLabel("Combine discounts");
        discountModeSelect.setItems("Additive (sum all)", "Best (take highest)");
        discountModeSelect.setValue("Additive (sum all)");
        discountModeSelect.setWidth("18rem");

        Button applyBtn = new Button("Apply Discount Policy", e -> {
            String mode = discountModeSelect.getValue() != null && discountModeSelect.getValue().startsWith("Best") ? "BEST" : "ADDITIVE";
            presenter.handleSetDiscountPolicy(eventId, new ArrayList<>(discountRules), mode, this);
        });

        section.add(
            new H3("Discount Policy"),
            new HorizontalLayout(currentDiscountLabel, currentDiscountSpan),
            addRow, paramBox,
            new Span("Active discounts:"), discountRuleList, clearBtn,
            new HorizontalLayout(discountModeSelect, applyBtn)
        );
        return section;
    }

    private void refreshDiscountList() {
        discountRuleList.removeAll();
        for (int i = 0; i < discountLabels.size(); i++) {
            final int idx = i;
            Button removeBtn = new Button("✕", e -> {
                discountRules.remove(idx);
                discountLabels.remove(idx);
                refreshDiscountList();
            });
            removeBtn.getStyle().set("color", "var(--lumo-error-color)").set("padding", "0 4px");
            discountRuleList.add(new HorizontalLayout(new Span("• " + discountLabels.get(i)), removeBtn));
        }
        if (discountLabels.isEmpty()) {
            discountRuleList.add(new Span("(none — will apply No Discount)"));
        }
    }

    // ── Pre-populate active rules from backend ────────────────────

    public void populatePurchaseRules(List<PurchasePolicy> rules, List<String> labels, String mode) {
        purchaseRules.clear();
        purchaseLabels.clear();
        purchaseRules.addAll(rules);
        purchaseLabels.addAll(labels);
        purchaseModeSelect.setValue(mode);
        refreshPurchaseList();
    }

    public void populateDiscountRules(List<DiscountPolicy> discounts, List<String> labels, String mode) {
        discountRules.clear();
        discountLabels.clear();
        discountRules.addAll(discounts);
        discountLabels.addAll(labels);
        discountModeSelect.setValue(mode);
        refreshDiscountList();
    }

    // ── Feedback ──────────────────────────────────────────────────

    public void showError(String message) {
        statusMessage.setText(message);
        statusMessage.getStyle().set("color", "var(--lumo-error-color)");
        statusMessage.setVisible(true);
    }

    public void showSuccess(String message) {
        statusMessage.setText(message);
        statusMessage.getStyle().set("color", "var(--lumo-success-color)");
        statusMessage.setVisible(true);
        presenter.handleLoadCurrentPolicies(eventId, this);
    }

    public void showCurrentPolicies(String purchaseDesc, String discountDesc) {
        currentPurchaseSpan.setText(purchaseDesc);
        currentDiscountSpan.setText(discountDesc);
    }
}
