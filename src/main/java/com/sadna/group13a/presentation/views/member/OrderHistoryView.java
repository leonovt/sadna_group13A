package com.sadna.group13a.presentation.views.member;

import com.sadna.group13a.application.DTO.OrderHistoryDTO;
import com.sadna.group13a.application.DTO.OrderHistoryItemDTO;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Route("member/orders")
@PageTitle("Order History")
public class OrderHistoryView extends VerticalLayout implements BeforeEnterObserver {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final OrderHistoryPresenter presenter;

    private final Grid<OrderHistoryDTO> ordersGrid = new Grid<>();
    private final Span emptyMessage = new Span("You have no past orders.");
    private final Span errorMessage = new Span();

    public OrderHistoryView(OrderHistoryPresenter presenter) {
        this.presenter = presenter;
        initView();
    }

    /**
     * Loads the orders on navigation rather than from the constructor: at
     * construction time the component is not yet attached and
     * {@code VaadinSession.getCurrent()} may be unavailable. {@code beforeEnter}
     * runs in the UI request thread with the session initialized.
     */
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        presenter.loadOrders(this);
    }

    private void initView() {
        setSizeFull();

        errorMessage.getStyle().set("color", "var(--lumo-error-color)");
        errorMessage.setVisible(false);
        emptyMessage.setVisible(false);

        // A single virtualized Grid (only visible rows are in the DOM) lists every
        // receipt; clicking a row expands a lightweight detail panel with its line
        // items, so large histories no longer create one Grid component per order.
        ordersGrid.addColumn(OrderHistoryDTO::receiptId).setHeader("Receipt").setAutoWidth(true);
        ordersGrid.addColumn(o -> o.purchaseDate() == null ? "—" : o.purchaseDate().format(DATE_FORMAT))
                .setHeader("Purchased").setAutoWidth(true);
        ordersGrid.addColumn(o -> o.items() == null ? 0 : o.items().size())
                .setHeader("Items").setAutoWidth(true);
        ordersGrid.addColumn(o -> String.format("$%.2f", o.totalPaid()))
                .setHeader("Total paid").setAutoWidth(true);
        ordersGrid.setItemDetailsRenderer(new ComponentRenderer<>(this::buildOrderDetails));
        ordersGrid.setSizeFull();
        ordersGrid.setVisible(false);

        add(new H2("Order History"), emptyMessage, errorMessage, ordersGrid);
    }

    public void showOrders(List<OrderHistoryDTO> orders) {
        errorMessage.setVisible(false);

        if (orders.isEmpty()) {
            ordersGrid.setVisible(false);
            emptyMessage.setVisible(true);
            return;
        }

        emptyMessage.setVisible(false);
        ordersGrid.setItems(orders);
        ordersGrid.setVisible(true);
    }

    /**
     * Renders one expanded receipt's line items as a compact list of labels.
     * Built lazily by the Grid only for rows the user actually expands, so it
     * adds no weight for collapsed orders.
     */
    private Component buildOrderDetails(OrderHistoryDTO order) {
        VerticalLayout details = new VerticalLayout();
        details.setPadding(true);
        details.setSpacing(false);

        List<OrderHistoryItemDTO> items = order.items() == null ? List.of() : order.items();
        if (items.isEmpty()) {
            details.add(new Span("No line items for this receipt."));
            return details;
        }

        for (OrderHistoryItemDTO item : items) {
            String eventDate = item.eventDate() == null ? "" : " (" + item.eventDate().format(DATE_FORMAT) + ")";
            details.add(new Span(String.format("%s%s — %s · zone %s · seat %s — $%.2f",
                    item.eventTitle(), eventDate, item.companyName(),
                    item.zoneName(), item.seatLabel(), item.pricePaid())));
        }
        return details;
    }

    public void showError(String message) {
        ordersGrid.setVisible(false);
        emptyMessage.setVisible(false);
        errorMessage.setText(message);
        errorMessage.setVisible(true);
    }
}
