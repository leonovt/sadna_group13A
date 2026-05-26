package com.sadna.group13a.presentation.views.company;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
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

    private final Span statusMessage     = new Span();
    private final Span companyNameValue  = new Span("—");
    private final Span companyStatusValue = new Span("—");
    private final Span companyDescValue  = new Span("—");

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

        // ── Status message ────────────────────────────────────────
        statusMessage.setVisible(false);
        statusMessage.getStyle().set("font-weight", "bold");

        // ── Info cards row ────────────────────────────────────────
        HorizontalLayout statsRow = new HorizontalLayout(
                statCard("Company", companyNameValue),
                statCard("Status",  companyStatusValue)
        );
        statsRow.setSpacing(true);

        // ── Navigation buttons ────────────────────────────────────
        Button staffBtn = new Button("Staff",
                e -> UI.getCurrent().navigate("company/" + companyId + "/staff"));
        Button eventsBtn = new Button("Events",
                e -> UI.getCurrent().navigate("company/" + companyId + "/events"));
        Button salesBtn = new Button("Sales Report",
                e -> UI.getCurrent().navigate("company/" + companyId + "/sales"));
        Button policiesBtn = new Button("Policies",
                e -> UI.getCurrent().navigate("company/" + companyId + "/policy"));

        HorizontalLayout navRow = new HorizontalLayout(staffBtn, eventsBtn, salesBtn, policiesBtn);
        navRow.setSpacing(true);

        add(
                header,
                statusMessage,
                statsRow,
                new H3("Description"), companyDescValue,
                new H3("Management"),  navRow
        );
    }

    private VerticalLayout statCard(String label, Span valueSpan) {
        valueSpan.getStyle()
                .set("font-size", "2rem")
                .set("font-weight", "bold");
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

    // ── View callbacks ────────────────────────────────────────────

    public void showError(String message) {
        statusMessage.setText(message);
        statusMessage.getStyle().set("color", "var(--lumo-error-color)");
        statusMessage.setVisible(true);
    }

    public void displayCompany(CompanyDTO company) {
        companyNameValue.setText(company.name());
        companyStatusValue.setText(company.status().toString());
        companyDescValue.setText(company.description());
    }
}
