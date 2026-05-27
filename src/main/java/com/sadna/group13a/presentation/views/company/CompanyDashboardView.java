package com.sadna.group13a.presentation.views.company;

import com.sadna.group13a.application.DTO.CompanyDTO;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route("company/:companyId")
@PageTitle("Company Dashboard")
public class CompanyDashboardView extends VerticalLayout implements BeforeEnterObserver {

    private final CompanyDashboardPresenter presenter;
    private String companyId;

    private final Span statusMessage      = new Span();
    private final Span companyNameValue   = new Span("—");
    private final Span companyStatusValue = new Span("—");
    private final Span companyDescValue   = new Span("—");

    public CompanyDashboardView(CompanyDashboardPresenter presenter) {
        this.presenter = presenter;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        companyId = event.getRouteParameters().get("companyId").orElse("");
        initView();
        presenter.loadDashboard(this, companyId);
    }

    private void initView() {
        removeAll();
        add(new Button("<- Home", e -> UI.getCurrent().navigate("")));
        setPadding(true);
        setSpacing(true);

        // ── Header ────────────────────────────────────────────────
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);
        header.setAlignItems(Alignment.CENTER);
        header.add(new H2("Company Dashboard"), new Button("Logout", e -> presenter.handleLogout()));

        statusMessage.setVisible(false);
        statusMessage.getStyle().set("font-weight", "bold");

        // ── Info cards ────────────────────────────────────────────
        HorizontalLayout statsRow = new HorizontalLayout(
                statCard("Company", companyNameValue),
                statCard("Status",  companyStatusValue)
        );
        statsRow.setSpacing(true);

        // ── Navigation ────────────────────────────────────────────
        Button staffBtn    = new Button("Staff",        e -> UI.getCurrent().navigate("company/" + companyId + "/staff"));
        Button eventsBtn   = new Button("Events",       e -> UI.getCurrent().navigate("company/" + companyId + "/events"));
        Button salesBtn    = new Button("Sales Report", e -> UI.getCurrent().navigate("company/" + companyId + "/sales"));
        Button ordersBtn   = new Button("Order History",e -> UI.getCurrent().navigate("company/" + companyId + "/orders"));
        Button policiesBtn = new Button("Policies",     e -> UI.getCurrent().navigate("company/" + companyId + "/policy"));
        Button rafflesBtn  = new Button("Raffles",      e -> UI.getCurrent().navigate("company/" + companyId + "/raffles"));

        HorizontalLayout navRow = new HorizontalLayout(staffBtn, eventsBtn, salesBtn, ordersBtn, policiesBtn, rafflesBtn);
        navRow.setSpacing(true);

        // ── Company status controls ───────────────────────────────
        Button suspendBtn = new Button("Suspend Company", e -> {
            statusMessage.setVisible(false);
            presenter.handleSuspendCompany(companyId, this);
        });
        suspendBtn.getStyle().set("color", "var(--lumo-error-color)");

        Button reopenBtn = new Button("Reopen Company", e -> {
            statusMessage.setVisible(false);
            presenter.handleReopenCompany(companyId, this);
        });

        HorizontalLayout statusControls = new HorizontalLayout(suspendBtn, reopenBtn);
        statusControls.setSpacing(true);

        add(
                header,
                statusMessage,
                statsRow,
                new H3("Description"), companyDescValue,
                new H3("Management"),  navRow,
                new H3("Company Status"), statusControls
        );
    }

    private VerticalLayout statCard(String label, Span valueSpan) {
        valueSpan.getStyle().set("font-size", "2rem").set("font-weight", "bold");
        Span labelSpan = new Span(label);
        labelSpan.getStyle().set("color", "var(--lumo-secondary-text-color)");
        VerticalLayout card = new VerticalLayout(valueSpan, labelSpan);
        card.setPadding(true);
        card.setSpacing(false);
        card.setAlignItems(Alignment.CENTER);
        card.getStyle()
                .set("border", "1px solid var(--lumo-contrast-20pct)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("min-width", "10rem");
        return card;
    }

    public void showError(String message) {
        statusMessage.setText(message);
        statusMessage.getStyle().set("color", "var(--lumo-error-color)");
        statusMessage.setVisible(true);
    }

    public void showSuccess(String message) {
        statusMessage.setText(message);
        statusMessage.getStyle().set("color", "var(--lumo-success-color)");
        statusMessage.setVisible(true);
    }

    public void displayCompany(CompanyDTO company) {
        companyNameValue.setText(company.name());
        companyStatusValue.setText(company.status().toString());
        companyDescValue.setText(company.description());
    }
}
