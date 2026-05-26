package com.sadna.group13a.presentation.views.admin;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.sadna.group13a.application.DTO.SystemAnalyticsDTO;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;

import java.util.List;

@Route("admin/analytics")
@PageTitle("Analytics")
public class AdminAnalyticsView extends VerticalLayout {

    private final AdminAnalyticsPresenter presenter;

    private final Span statusMessage = new Span();

    // System overview stat cards
    private final Span totalUsersValue     = new Span("—");
    private final Span activeQueuesValue   = new Span("—");
    private final Span activeCompaniesValue = new Span("—");
    private final Span publishedEventsValue = new Span("—");

    // Revenue stats
    private final Span totalRevenueValue = new Span("—");
    private final Span totalOrdersValue  = new Span("—");

    // Log containers
    private final VerticalLayout eventLogContainer = new VerticalLayout();
    private final VerticalLayout errorLogContainer = new VerticalLayout();

    public AdminAnalyticsView(AdminAnalyticsPresenter presenter) {
        this.presenter = presenter;
        initView();
        addAttachListener(e -> presenter.loadAnalytics(this));
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
        header.add(new H2("Analytics"), new RouterLink("← Back to Dashboard", AdminDashboardView.class));

        // ── Status message ────────────────────────────────────────
        statusMessage.setVisible(false);
        statusMessage.getStyle().set("font-weight", "bold");

        // ── System overview ───────────────────────────────────────
        HorizontalLayout systemStatsRow = new HorizontalLayout(
                statCard("Total Users",       totalUsersValue),
                statCard("Active Queues",     activeQueuesValue),
                statCard("Active Companies",  activeCompaniesValue),
                statCard("Published Events",  publishedEventsValue)
        );
        systemStatsRow.setSpacing(true);

        // ── Revenue ───────────────────────────────────────────────
        HorizontalLayout revenueRow = new HorizontalLayout(
                statCard("Total Revenue",  totalRevenueValue),
                statCard("Total Orders",   totalOrdersValue)
        );
        revenueRow.setSpacing(true);

        // ── Event log ─────────────────────────────────────────────
        eventLogContainer.setPadding(false);
        eventLogContainer.setSpacing(false);

        // ── Error log ─────────────────────────────────────────────
        errorLogContainer.setPadding(false);
        errorLogContainer.setSpacing(false);

        add(
                header,
                statusMessage,
                new H3("System Overview"),  systemStatsRow,
                new H3("Revenue"),          revenueRow,
                new H3("Event Log"),        eventLogContainer,
                new H3("Error Log"),        errorLogContainer
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

    private void populateLog(VerticalLayout container, List<String> entries, String emptyMessage) {
        container.removeAll();
        if (entries.isEmpty()) {
            container.add(new Span(emptyMessage));
            return;
        }
        for (int i = entries.size() - 1; i >= 0; i--) {
            Span entry = new Span(entries.get(i));
            entry.getStyle()
                    .set("font-family", "monospace")
                    .set("font-size", "var(--lumo-font-size-s)")
                    .set("color", "var(--lumo-secondary-text-color)");
            container.add(entry);
        }
    }

    // ── View callbacks ────────────────────────────────────────────

    public void showError(String message) {
        statusMessage.setText(message);
        statusMessage.getStyle().set("color", "var(--lumo-error-color)");
        statusMessage.setVisible(true);
    }

    public void displayAnalytics(SystemAnalyticsDTO analytics) {
        totalUsersValue.setText(String.valueOf(analytics.totalUsers()));
        activeQueuesValue.setText(String.valueOf(analytics.activeQueues()));
        activeCompaniesValue.setText(String.valueOf(analytics.activeCompanies()));
        publishedEventsValue.setText(String.valueOf(analytics.publishedEvents()));
    }

    public void displayRevenue(double totalRevenue, int orderCount) {
        totalRevenueValue.setText(String.format("$%.2f", totalRevenue));
        totalOrdersValue.setText(String.valueOf(orderCount));
    }

    public void displayEventLog(List<String> entries) {
        populateLog(eventLogContainer, entries, "No events recorded.");
    }

    public void displayErrorLog(List<String> entries) {
        populateLog(errorLogContainer, entries, "No errors recorded.");
    }
}
