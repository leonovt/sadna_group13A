package com.sadna.group13a.presentation.views.admin;

import com.sadna.group13a.application.DTO.OrderHistoryDTO;
import com.sadna.group13a.application.DTO.SuspensionDTO;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
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

    // ── Deactivate / Reactivate ───────────────────────────────────
    private final TextField usernameField = new TextField("Username");

    // ── Suspend / Lift ────────────────────────────────────────────
    private final TextField suspendUsernameField = new TextField("Username");
    private final TextField durationDaysField    = new TextField("Duration (days, blank = permanent)");

    // ── Send message ──────────────────────────────────────────────
    private final TextField msgUsernameField = new TextField("Username");
    private final TextArea  messageField     = new TextArea("Message");

    // ── Grids ─────────────────────────────────────────────────────
    private final Grid<OrderHistoryDTO> historyGrid     = new Grid<>(OrderHistoryDTO.class, false);
    private final Grid<SuspensionDTO>   suspensionGrid  = new Grid<>(SuspensionDTO.class, false);

    public AdminUserManagementView(AdminUserManagementPresenter presenter) {
        this.presenter = presenter;
        initView();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!presenter.hasAdminAccess()) {
            event.forwardTo(LoginView.class);
            return;
        }
        presenter.loadPurchaseHistory(this);
        presenter.loadSuspensions(this);
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

        statusMessage.setVisible(false);
        statusMessage.getStyle().set("font-weight", "bold");

        // ── Deactivate / Reactivate ───────────────────────────────
        Button deactivateBtn = new Button("Deactivate", e -> {
            statusMessage.setVisible(false);
            presenter.handleDeactivateUser(usernameField.getValue(), this);
        });
        Button reactivateBtn = new Button("Reactivate", e -> {
            statusMessage.setVisible(false);
            presenter.handleReactivateUser(usernameField.getValue(), this);
        });
        HorizontalLayout deactivateRow = new HorizontalLayout(usernameField, deactivateBtn, reactivateBtn);
        deactivateRow.setAlignItems(Alignment.BASELINE);

        // ── Suspend / Lift ────────────────────────────────────────
        durationDaysField.setPlaceholder("e.g. 7");
        durationDaysField.setWidth("14rem");
        Button suspendBtn = new Button("Suspend", e -> {
            statusMessage.setVisible(false);
            presenter.handleSuspendUser(suspendUsernameField.getValue(), durationDaysField.getValue(), this);
        });
        Button liftBtn = new Button("Lift Suspension", e -> {
            statusMessage.setVisible(false);
            presenter.handleLiftSuspension(suspendUsernameField.getValue(), this);
        });
        HorizontalLayout suspendRow = new HorizontalLayout(suspendUsernameField, durationDaysField, suspendBtn, liftBtn);
        suspendRow.setAlignItems(Alignment.BASELINE);

        // ── Suspension grid ───────────────────────────────────────
        suspensionGrid.addColumn(SuspensionDTO::username).setHeader("Username").setFlexGrow(1);
        suspensionGrid.addColumn(s -> s.startDate().toLocalDate().toString()).setHeader("Suspended On");
        suspensionGrid.addColumn(s -> s.endDate() != null ? s.endDate().toLocalDate().toString() : "Permanent").setHeader("Until");
        suspensionGrid.addColumn(s -> s.duration() != null ? s.duration().toDays() + " days" : "Permanent").setHeader("Duration");
        suspensionGrid.setWidthFull();
        suspensionGrid.setMaxHeight("250px");

        // ── Send message ──────────────────────────────────────────
        messageField.setWidthFull();
        messageField.setMaxHeight("6rem");
        Button sendMsgBtn = new Button("Send Message", e -> {
            statusMessage.setVisible(false);
            presenter.handleSendMessage(msgUsernameField.getValue(), messageField.getValue(), this);
        });
        HorizontalLayout msgRow = new HorizontalLayout(msgUsernameField, sendMsgBtn);
        msgRow.setAlignItems(Alignment.BASELINE);

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
                new H3("Deactivate / Reactivate User"), deactivateRow,
                new H3("Suspend User"), suspendRow,
                new H3("Active Suspensions"), suspensionGrid,
                new H3("Send Message to User"), msgRow, messageField,
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

    public void displaySuspensions(List<SuspensionDTO> suspensions) {
        suspensionGrid.setItems(suspensions);
    }
}
