package com.sadna.group13a.presentation.views.company;

import com.sadna.group13a.application.DTO.StaffMemberDTO;
import com.sadna.group13a.domain.Aggregates.Company.CompanyPermission;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
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

    // ── Manager actions ───────────────────────────────────────────
    private final TextField managerUsernameField = new TextField("Username");
    private final CheckboxGroup<CompanyPermission> managerPermissionsGroup = new CheckboxGroup<>("Permissions for new manager");

    // ── Owner actions ─────────────────────────────────────────────
    private final TextField ownerUsernameField = new TextField("Username");

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
        removeAll();
        add(new Button("<- Home", e -> UI.getCurrent().navigate("")));
        setPadding(true);
        setSpacing(true);

        // ── Header ────────────────────────────────────────────────
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);
        header.setAlignItems(Alignment.CENTER);
        header.add(new H2("Staff Management"),
                new Button("← Back to Dashboard", e -> presenter.handleBack(companyId)));

        statusMessage.setVisible(false);
        statusMessage.getStyle().set("font-weight", "bold");

        // ── Staff grid ────────────────────────────────────────────
        staffGrid.addColumn(StaffMemberDTO::userId).setHeader("User ID").setFlexGrow(1);
        staffGrid.addColumn(s -> s.username() != null ? s.username() : "—").setHeader("Username").setFlexGrow(1);
        staffGrid.addColumn(s -> s.role().name()).setHeader("Role").setFlexGrow(1);
        staffGrid.addColumn(s -> s.permissions() == null || s.permissions().isEmpty()
                ? "—" : s.permissions().toString()).setHeader("Permissions").setFlexGrow(2);
        staffGrid.setWidthFull();
        staffGrid.setMaxHeight("400px");

        // ── Manager actions ───────────────────────────────────────
        managerPermissionsGroup.setItems(CompanyPermission.values());
        managerPermissionsGroup.setItemLabelGenerator(p -> p.name().replace('_', ' '));

        Button appointManagerBtn = new Button("Appoint Manager", e -> {
            statusMessage.setVisible(false);
            presenter.handleAppointManager(managerUsernameField.getValue(), managerPermissionsGroup.getValue(), companyId, this);
        });
        Button fireStaffBtn = new Button("Fire", e -> {
            statusMessage.setVisible(false);
            presenter.handleFireStaff(managerUsernameField.getValue(), companyId, this);
        });
        HorizontalLayout managerBtnRow = new HorizontalLayout(managerUsernameField, appointManagerBtn, fireStaffBtn);
        managerBtnRow.setAlignItems(Alignment.BASELINE);
        VerticalLayout managerRow = new VerticalLayout(managerBtnRow, managerPermissionsGroup);
        managerRow.setPadding(false);
        managerRow.setSpacing(false);

        // ── Owner actions ─────────────────────────────────────────
        Button appointOwnerBtn = new Button("Appoint Owner", e -> {
            statusMessage.setVisible(false);
            presenter.handleAppointOwner(ownerUsernameField.getValue(), companyId, this);
        });
        Button removeOwnerBtn = new Button("Remove Owner", e -> {
            statusMessage.setVisible(false);
            presenter.handleRemoveOwner(ownerUsernameField.getValue(), companyId, this);
        });
        HorizontalLayout ownerRow = new HorizontalLayout(ownerUsernameField, appointOwnerBtn, removeOwnerBtn);
        ownerRow.setAlignItems(Alignment.BASELINE);

        // ── Nomination acceptance (for the logged-in user) ────────
        Button acceptBtn = new Button("Accept My Nomination", e -> {
            statusMessage.setVisible(false);
            presenter.handleAcceptNomination(companyId, this);
        });
        Button rejectBtn = new Button("Reject My Nomination", e -> {
            statusMessage.setVisible(false);
            presenter.handleRejectNomination(companyId, this);
        });
        rejectBtn.getStyle().set("color", "var(--lumo-error-color)");
        HorizontalLayout nominationRow = new HorizontalLayout(acceptBtn, rejectBtn);
        nominationRow.setSpacing(true);

        // ── Resign ────────────────────────────────────────────────
        Button resignBtn = new Button("Resign from Company",
                e -> presenter.handleResign(companyId, this));
        resignBtn.getStyle().set("color", "var(--lumo-error-color)");

        add(
                header,
                statusMessage,
                new H3("Current Staff"), staffGrid,
                new H3("Manager Actions"), managerRow,
                new H3("Owner Actions"), ownerRow,
                new H3("My Nomination"), nominationRow,
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
