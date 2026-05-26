package com.sadna.group13a.presentation.views.company;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.sadna.group13a.application.DTO.OrderHistoryDTO;
import com.sadna.group13a.application.DTO.SalesReportDTO;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route("company/:companyId/sales")
@PageTitle("Sales Report")
public class SalesReportView extends VerticalLayout implements BeforeEnterObserver {

    private final SalesReportPresenter presenter;
    private String companyId;

    private final Span statusMessage    = new Span();
    private final Span companyNameValue = new Span("—");
    private final Span totalOrdersValue = new Span("—");
    private final Span totalRevenueValue = new Span("—");
    private final Grid<OrderHistoryDTO> ordersGrid = new Grid<>(OrderHistoryDTO.class, false);

    public SalesReportView(SalesReportPresenter presenter) {
        this.presenter = presenter;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        companyId = event.getRouteParameters().get("companyId").orElse("");
        initView();
        presenter.loadReport(this, companyId);
    }

    private void initView() {
        add(new Button("<- Home", e -> UI.getCurrent().navigate("")));
        removeAll();
        setPadding(true);
        setSpacing(true);

        // ── Header ────────────────────────────────────────────────
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);
        header.setAlignItems(Alignment.CENTER);
        Button backBtn = new Button("← Back to Dashboard", e -> presenter.handleBack(companyId));
        header.add(new H2("Sales Report"), backBtn);

        // ── Status message ────────────────────────────────────────
        statusMessage.setVisible(false);
        statusMessage.getStyle().set("font-weight", "bold");

        // ── Summary stat cards ────────────────────────────────────
        HorizontalLayout statsRow = new HorizontalLayout(
                statCard("Company",       companyNameValue),
                statCard("Total Orders",  totalOrdersValue),
                statCard("Total Revenue", totalRevenueValue)
        );
        statsRow.setSpacing(true);

        // ── Orders grid ───────────────────────────────────────────
        ordersGrid.addColumn(OrderHistoryDTO::receiptId)
                .setHeader("Receipt ID").setFlexGrow(1);
        ordersGrid.addColumn(OrderHistoryDTO::userId)
                .setHeader("User ID").setFlexGrow(1);
        ordersGrid.addColumn(h -> h.purchaseDate().toLocalDate().toString())
                .setHeader("Date");
        ordersGrid.addColumn(h -> String.format("$%.2f", h.totalPaid()))
                .setHeader("Total Paid");
        ordersGrid.setWidthFull();
        ordersGrid.setMaxHeight("400px");

        add(
                header,
                statusMessage,
                new H3("Summary"),  statsRow,
                new H3("Orders"),   ordersGrid
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

    public void displayReport(SalesReportDTO report) {
        companyNameValue.setText(report.companyName());
        totalOrdersValue.setText(String.valueOf(report.totalOrders()));
        totalRevenueValue.setText(String.format("$%.2f", report.totalRevenue()));
        ordersGrid.setItems(report.orders());
    }
}
