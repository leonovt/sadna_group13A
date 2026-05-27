package com.sadna.group13a.presentation.views.company;

import com.sadna.group13a.application.DTO.OrderHistoryDTO;
import com.sadna.group13a.presentation.views.auth.LoginView;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.util.List;

@Route("company/:companyId/orders")
@PageTitle("Order History")
public class CompanyOrderHistoryView extends VerticalLayout implements BeforeEnterObserver {

    private final CompanyOrderHistoryPresenter presenter;
    private String companyId;

    private final Span statusMessage = new Span();
    private final Grid<OrderHistoryDTO> ordersGrid = new Grid<>(OrderHistoryDTO.class, false);

    public CompanyOrderHistoryView(CompanyOrderHistoryPresenter presenter) {
        this.presenter = presenter;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        companyId = event.getRouteParameters().get("companyId").orElse("");
        if (!presenter.hasAccess()) {
            event.forwardTo(LoginView.class);
            return;
        }
        initView();
        presenter.loadOrders(this, companyId);
    }

    private void initView() {
        removeAll();
        add(new Button("<- Back", e -> UI.getCurrent().navigate("company/" + companyId)));
        setPadding(true);
        setSpacing(true);

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);
        header.setAlignItems(Alignment.CENTER);
        header.add(new H2("Order History"));

        statusMessage.setVisible(false);
        statusMessage.getStyle().set("font-weight", "bold");

        ordersGrid.addColumn(OrderHistoryDTO::receiptId).setHeader("Receipt ID").setFlexGrow(2);
        ordersGrid.addColumn(OrderHistoryDTO::userId).setHeader("Buyer ID").setFlexGrow(1);
        ordersGrid.addColumn(h -> h.purchaseDate().toLocalDate().toString()).setHeader("Date");
        ordersGrid.addColumn(h -> String.format("$%.2f", h.totalPaid())).setHeader("Total");
        ordersGrid.setWidthFull();

        add(header, statusMessage, ordersGrid);
    }

    public void showError(String message) {
        statusMessage.setText(message);
        statusMessage.getStyle().set("color", "var(--lumo-error-color)");
        statusMessage.setVisible(true);
    }

    public void displayOrders(List<OrderHistoryDTO> orders) {
        ordersGrid.setItems(orders);
    }
}
