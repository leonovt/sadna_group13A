package com.sadna.group13a.presentation.views.admin;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.sadna.group13a.application.DTO.SystemAnalyticsDTO;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;

import java.util.List;

@Route("admin")
@PageTitle("Admin Dashboard")
public class AdminDashboardView extends VerticalLayout {

    private final AdminDashboardPresenter presenter;

    private final Span totalUsersValue    = new Span("—");
    private final Span activeQueuesValue  = new Span("—");
    private final Span activeCompaniesValue = new Span("—");
    private final Span publishedEventsValue = new Span("—");

    private final Span statusMessage = new Span();

    private final TextField userField    = new TextField("Username");
    private final TextField eventField   = new TextField("Event ID");
    private final TextField companyField = new TextField("Company ID");

    private final VerticalLayout logContainer = new VerticalLayout();

    public AdminDashboardView(AdminDashboardPresenter presenter) {
        this.presenter = presenter;
        initView();
        addAttachListener(e -> presenter.loadDashboard(this));
    }

    private void initView() {
        add(new Button("<- Home", e -> UI.getCurrent().navigate("")));
        setPadding(true);
        setSpacing(true);

        // ── Header ────────────────────────────────────────────────
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);
        header.setAlignItems(Alignment.CENTER);
        header.add(new H2("Admin Dashboard"), new Button("Logout", e -> presenter.handleLogout()));

        // ── Status message ────────────────────────────────────────
        statusMessage.setVisible(false);
        statusMessage.getStyle().set("font-weight", "bold");

        // ── System overview ───────────────────────────────────────
        HorizontalLayout statsRow = new HorizontalLayout(
                statCard("Total Users",       totalUsersValue),
                statCard("Active Queues",     activeQueuesValue),
                statCard("Active Companies",  activeCompaniesValue),
                statCard("Published Events",  publishedEventsValue)
        );
        statsRow.setSpacing(true);

        // ── Navigation ────────────────────────────────────────────
        HorizontalLayout navRow = new HorizontalLayout(
                new RouterLink("User Management", AdminUserManagementView.class),
                new RouterLink("Analytics",       AdminAnalyticsView.class),
                new RouterLink("Queue Control",   AdminQueueView.class)
        );
        navRow.setSpacing(true);

        // ── Quick actions ─────────────────────────────────────────
        Button deactivateBtn = new Button("Deactivate", e -> {
            statusMessage.setVisible(false);
            presenter.handleDeactivateUser(userField.getValue(), this);
        });
        Button reactivateBtn = new Button("Reactivate", e -> {
            statusMessage.setVisible(false);
            presenter.handleReactivateUser(userField.getValue(), this);
        });
        HorizontalLayout userActions = new HorizontalLayout(userField, deactivateBtn, reactivateBtn);
        userActions.setAlignItems(Alignment.BASELINE);

        Button cancelEventBtn = new Button("Cancel Event", e -> {
            statusMessage.setVisible(false);
            presenter.handleCancelEvent(eventField.getValue(), this);
        });
        HorizontalLayout eventActions = new HorizontalLayout(eventField, cancelEventBtn);
        eventActions.setAlignItems(Alignment.BASELINE);

        Button closeCompanyBtn = new Button("Close Company", e -> {
            statusMessage.setVisible(false);
            presenter.handleCloseCompany(companyField.getValue(), this);
        });
        HorizontalLayout companyActions = new HorizontalLayout(companyField, closeCompanyBtn);
        companyActions.setAlignItems(Alignment.BASELINE);

        // ── Activity log ──────────────────────────────────────────
        logContainer.setPadding(false);
        logContainer.setSpacing(false);

        add(
                header,
                statusMessage,
                new H3("System Overview"), statsRow,
                new H3("Admin Pages"),     navRow,
                new H3("Quick Actions"),   userActions, eventActions, companyActions,
                new H3("Recent Activity"), logContainer
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

    public void showSuccess(String message) {
        statusMessage.setText(message);
        statusMessage.getStyle().set("color", "var(--lumo-success-color)");
        statusMessage.setVisible(true);
    }

    public void displayAnalytics(SystemAnalyticsDTO analytics) {
        totalUsersValue.setText(String.valueOf(analytics.totalUsers()));
        activeQueuesValue.setText(String.valueOf(analytics.activeQueues()));
        activeCompaniesValue.setText(String.valueOf(analytics.activeCompanies()));
        publishedEventsValue.setText(String.valueOf(analytics.publishedEvents()));
    }

    public void displayEventLog(List<String> entries) {
        logContainer.removeAll();
        if (entries.isEmpty()) {
            logContainer.add(new Span("No activity recorded."));
            return;
        }
        int start = Math.max(0, entries.size() - 10);
        for (int i = entries.size() - 1; i >= start; i--) {
            Span entry = new Span(entries.get(i));
            entry.getStyle()
                    .set("font-family", "monospace")
                    .set("font-size", "var(--lumo-font-size-s)")
                    .set("color", "var(--lumo-secondary-text-color)");
            logContainer.add(entry);
        }
    }
}
