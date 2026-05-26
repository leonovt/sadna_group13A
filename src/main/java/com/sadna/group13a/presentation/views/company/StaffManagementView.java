package com.sadna.group13a.presentation.views.company;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.sadna.group13a.application.DTO.StaffMemberDTO;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.util.List;

@Route("company/:companyId/staff")
@PageTitle("Staff Management")
public class StaffManagementView extends VerticalLayout implements BeforeEnterObserver {

    private final StaffManagementPresenter presenter;
    private String companyId;

    private final Span statusMessage = new Span();
    private final Grid<StaffMemberDTO> staffGrid = new Grid<>(StaffMemberDTO.class, false);
    private final TextField usernameField = new TextField("Username");

    public StaffManagementView(StaffManagementPresenter presenter) {
        this.presenter = presenter;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        companyId = event.getRouteParameters().get("companyId").orElse("");
        initView();
        presenter.loadStaff(this, companyId);
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
        header.add(new H2("Staff Management"), backBtn);

        // ── Status message ────────────────────────────────────────
        statusMessage.setVisible(false);
        statusMessage.getStyle().set("font-weight", "bold");

        // ── Staff grid ────────────────────────────────────────────
        staffGrid.addColumn(StaffMemberDTO::userId).setHeader("User ID").setFlexGrow(1);
        staffGrid.addColumn(s -> s.role().name()).setHeader("Role").setFlexGrow(1);
        staffGrid.addColumn(s -> s.permissions() == null || s.permissions().isEmpty()
                ? "—"
                : s.permissions().toString()).setHeader("Permissions").setFlexGrow(2);
        staffGrid.setWidthFull();
        staffGrid.setMaxHeight("400px");

        // ── Action section ────────────────────────────────────────
        Button appointManagerBtn = new Button("Appoint Manager", e -> {
            statusMessage.setVisible(false);
            presenter.handleAppointManager(usernameField.getValue(), companyId, this);
        });
        Button fireStaffBtn = new Button("Fire Staff", e -> {
            statusMessage.setVisible(false);
            presenter.handleFireStaff(usernameField.getValue(), companyId, this);
        });
        HorizontalLayout actions = new HorizontalLayout(usernameField, appointManagerBtn, fireStaffBtn);
        actions.setAlignItems(Alignment.BASELINE);

        // ── Resign button ─────────────────────────────────────────
        Button resignBtn = new Button("Resign from Company", e -> presenter.handleResign(companyId, this));
        resignBtn.getStyle().set("color", "var(--lumo-error-color)");

        add(
                header,
                statusMessage,
                new H3("Current Staff"), staffGrid,
                new H3("Staff Actions"), actions,
                resignBtn
        );
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

    public void displayStaff(List<StaffMemberDTO> staff) {
        staffGrid.setItems(staff);
    }
}
