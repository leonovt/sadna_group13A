package com.sadna.group13a.presentation.views.member;

import com.sadna.group13a.application.DTO.OrderHistoryDTO;
import com.sadna.group13a.application.DTO.OrderHistoryItemDTO;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Route("member/orders")
@PageTitle("Order History")
public class OrderHistoryView extends VerticalLayout {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final OrderHistoryPresenter presenter;

    private final VerticalLayout ordersContainer = new VerticalLayout();
    private final Span emptyMessage = new Span("You have no past orders.");
    private final Span errorMessage = new Span();

    public OrderHistoryView(OrderHistoryPresenter presenter) {
        this.presenter = presenter;
        initView();
        presenter.loadOrders(this);
    }

    private void initView() {
        setSizeFull();

        errorMessage.getStyle().set("color", "var(--lumo-error-color)");
        errorMessage.setVisible(false);
        emptyMessage.setVisible(false);
        ordersContainer.setPadding(false);

        add(new H2("Order History"), emptyMessage, errorMessage, ordersContainer);
    }

    public void showOrders(List<OrderHistoryDTO> orders) {
        errorMessage.setVisible(false);
        ordersContainer.removeAll();

        if (orders.isEmpty()) {
            emptyMessage.setVisible(true);
            return;
        }

        emptyMessage.setVisible(false);
        for (OrderHistoryDTO order : orders) {
            ordersContainer.add(buildOrderCard(order));
        }
    }

    private Component buildOrderCard(OrderHistoryDTO order) {
        VerticalLayout card = new VerticalLayout();
        card.setPadding(true);
        card.setSpacing(false);
        card.getStyle()
            .set("border", "1px solid var(--lumo-contrast-20pct)")
            .set("border-radius", "var(--lumo-border-radius-m)")
            .set("margin-bottom", "var(--lumo-space-m)");

        H4 header = new H4("Receipt " + order.receiptId());
        Span meta = new Span(String.format("Purchased %s • Total paid: $%.2f",
            order.purchaseDate() == null ? "—" : order.purchaseDate().format(DATE_FORMAT),
            order.totalPaid()));

        Grid<OrderHistoryItemDTO> itemsGrid = new Grid<>();
        itemsGrid.addColumn(OrderHistoryItemDTO::eventTitle).setHeader("Event");
        itemsGrid.addColumn(i -> i.eventDate() == null ? "" : i.eventDate().format(DATE_FORMAT)).setHeader("Event date");
        itemsGrid.addColumn(OrderHistoryItemDTO::companyName).setHeader("Company");
        itemsGrid.addColumn(OrderHistoryItemDTO::zoneName).setHeader("Zone");
        itemsGrid.addColumn(OrderHistoryItemDTO::seatLabel).setHeader("Seat");
        itemsGrid.addColumn(i -> String.format("$%.2f", i.pricePaid())).setHeader("Price");
        itemsGrid.setItems(order.items() == null ? List.of() : order.items());
        itemsGrid.setAllRowsVisible(true);

        card.add(header, meta, itemsGrid);
        return card;
    }

    public void showError(String message) {
        ordersContainer.removeAll();
        emptyMessage.setVisible(false);
        errorMessage.setText(message);
        errorMessage.setVisible(true);
    }
}
