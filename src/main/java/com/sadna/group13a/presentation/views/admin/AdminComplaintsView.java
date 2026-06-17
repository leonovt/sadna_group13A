package com.sadna.group13a.presentation.views.admin;

import com.sadna.group13a.application.DTO.ComplaintDTO;
import com.sadna.group13a.presentation.views.auth.LoginView;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.util.List;

/** Admin view to read and respond to buyer complaints (II.6.3). */
@Route("admin/complaints")
@PageTitle("Complaints")
public class AdminComplaintsView extends VerticalLayout implements BeforeEnterObserver {

    private final AdminComplaintsPresenter presenter;

    private final Span statusMessage = new Span();
    private final Grid<ComplaintDTO> grid = new Grid<>(ComplaintDTO.class, false);
    private final TextArea responseField = new TextArea("Response");

    private ComplaintDTO selected;

    public AdminComplaintsView(AdminComplaintsPresenter presenter) {
        this.presenter = presenter;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!presenter.hasAdminAccess()) {
            event.forwardTo(LoginView.class);
            return;
        }
        initView();
        presenter.loadComplaints(this);
    }

    private void initView() {
        removeAll();
        add(new Button("<- Home", e -> UI.getCurrent().navigate("")));
        setPadding(true);
        setSpacing(true);

        statusMessage.setVisible(false);
        statusMessage.getStyle().set("font-weight", "bold");

        grid.addColumn(ComplaintDTO::complainantUsername).setHeader("From").setFlexGrow(0);
        grid.addColumn(ComplaintDTO::subject).setHeader("Subject").setFlexGrow(1);
        grid.addColumn(ComplaintDTO::message).setHeader("Message").setFlexGrow(2);
        grid.addColumn(ComplaintDTO::status).setHeader("Status").setFlexGrow(0);
        grid.addColumn(c -> c.adminResponse() == null ? "—" : c.adminResponse())
                .setHeader("Response").setFlexGrow(2);
        grid.setWidthFull();
        grid.setMaxHeight("400px");
        grid.asSingleSelect().addValueChangeListener(e -> selected = e.getValue());

        responseField.setWidthFull();
        Button respondBtn = new Button("Send Response & Resolve", e -> {
            statusMessage.setVisible(false);
            if (selected == null) {
                showError("Select a complaint first.");
                return;
            }
            presenter.handleRespond(selected.id(), responseField.getValue(), this);
            responseField.clear();
        });

        HorizontalLayout respondRow = new HorizontalLayout(responseField, respondBtn);
        respondRow.setAlignItems(Alignment.BASELINE);
        respondRow.setWidthFull();

        add(new H2("Complaints"), statusMessage, grid, new H3("Respond"), respondRow);
    }

    public void displayComplaints(List<ComplaintDTO> complaints) {
        grid.setItems(complaints);
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
}
