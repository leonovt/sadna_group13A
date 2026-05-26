package com.sadna.group13a.presentation.views.admin;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.sadna.group13a.application.DTO.OrderHistoryDTO;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.sadna.group13a.presentation.views.auth.LoginView;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;

import java.util.List;

@Route("admin/users")
@PageTitle("User Management")
public class AdminUserManagementView extends VerticalLayout implements BeforeEnterObserver {

    private final AdminUserManagementPresenter presenter;

    private final Span statusMessage = new Span();
    private final TextField usernameField = new TextField("Username");
    private final Grid<OrderHistoryDTO> historyGrid = new Grid<>(OrderHistoryDTO.class, false);

    public AdminUserManagementView(AdminUserManagementPresenter presenter) {
        this.presenter = presenter;
        initView();
    }

    // Issue #1 & #3 fix: gate access and load data before the view is rendered
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!presenter.hasAdminAccess()) {
            event.forwardTo(LoginView.class);
            return;
        }
        presenter.loadPurchaseHistory(this);
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
        header.add(new H2("User Management"), new RouterLink("← Back to Dashboard", AdminDashboardView.class));

        // ── Status message ────────────────────────────────────────
        statusMessage.setVisible(false);
        statusMessage.getStyle().set("font-weight", "bold");

        // ── User actions ──────────────────────────────────────────
        Button deactivateBtn = new Button("Deactivate", e -> {
            statusMessage.setVisible(false);
            presenter.handleDeactivateUser(usernameField.getValue(), this);
        });
        Button reactivateBtn = new Button("Reactivate", e -> {
            statusMessage.setVisible(false);
            presenter.handleReactivateUser(usernameField.getValue(), this);
        });
        HorizontalLayout userActions = new HorizontalLayout(usernameField, deactivateBtn, reactivateBtn);
        userActions.setAlignItems(Alignment.BASELINE);

        // ── Purchase history grid ─────────────────────────────────
        historyGrid.addColumn(OrderHistoryDTO::receiptId).setHeader("Receipt ID").setFlexGrow(1);
        historyGrid.addColumn(OrderHistoryDTO::userId).setHeader("User ID").setFlexGrow(1);
        historyGrid.addColumn(h -> h.purchaseDate().toLocalDate().toString()).setHeader("Date");
        historyGrid.addColumn(h -> String.format("$%.2f", h.totalPaid())).setHeader("Total");
        historyGrid.setWidthFull();
        historyGrid.setMaxHeight("400px");

        add(
                header,
                statusMessage,
                new H3("User Actions"), userActions,
                new H3("Global Purchase History"), historyGrid
        );
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

    public void displayPurchaseHistory(List<OrderHistoryDTO> orders) {
        historyGrid.setItems(orders);
    }
}
