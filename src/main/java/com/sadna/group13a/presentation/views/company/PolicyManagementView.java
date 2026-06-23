package com.sadna.group13a.presentation.views.company;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.sadna.group13a.application.DTO.StaffMemberDTO;
import com.sadna.group13a.domain.Aggregates.Company.CompanyPermission;
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

@Route("company/:companyId/policy")
@PageTitle("Policy Management")
public class PolicyManagementView extends VerticalLayout implements BeforeEnterObserver {

    private final PolicyManagementPresenter presenter;
    private String companyId;

    private final Span statusMessage = new Span();
    private final Grid<StaffMemberDTO> staffGrid = new Grid<>(StaffMemberDTO.class, false);
    private final TextField managerUsernameField = new TextField("Manager Username");
    private final CheckboxGroup<CompanyPermission> permissionsGroup = new CheckboxGroup<>("Permissions");

    public PolicyManagementView(PolicyManagementPresenter presenter) {
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
        Button backBtn = new Button("← Back to Dashboard", e -> presenter.handleBack(companyId));
        header.add(new H2("Policy Management"), backBtn);

        // ── Status message ────────────────────────────────────────
        statusMessage.setVisible(false);
        statusMessage.getStyle().set("font-weight", "bold");

        // ── Staff grid ────────────────────────────────────────────
        staffGrid.addColumn(StaffMemberDTO::userId).setHeader("User ID").setFlexGrow(1);
        staffGrid.addColumn(StaffMemberDTO::role).setHeader("Role").setFlexGrow(1);
        staffGrid.addColumn(s -> s.permissions().toString()).setHeader("Permissions").setFlexGrow(2);
        staffGrid.addColumn(s -> s.supervisorId() != null ? s.supervisorId() : "—").setHeader("Supervisor").setFlexGrow(1);
        staffGrid.setWidthFull();
        staffGrid.setMaxHeight("400px");

        // ── Update permissions action ─────────────────────────────
        permissionsGroup.setItems(CompanyPermission.values());

        Button updateBtn = new Button("Update Permissions", e -> {
            statusMessage.setVisible(false);
            presenter.handleUpdatePermissions(
                    managerUsernameField.getValue(),
                    permissionsGroup.getValue(),
                    companyId,
                    this
            );
        });

        HorizontalLayout actionRow = new HorizontalLayout(managerUsernameField, updateBtn);
        actionRow.setAlignItems(Alignment.BASELINE);

        add(
                header,
                statusMessage,
                new H3("Staff Members"), staffGrid,
                new H3("Update Permissions"), actionRow, permissionsGroup
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

    public void displayStaff(List<StaffMemberDTO> staff) {
        staffGrid.setItems(staff);
    }
}
