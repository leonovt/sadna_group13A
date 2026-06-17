package com.sadna.group13a.presentation.views.member;

import com.sadna.group13a.application.DTO.ComplaintDTO;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.util.List;

/** Member view to submit a complaint to the administrators and track its status (II.3.3). */
@Route("member/complaints")
@PageTitle("My Complaints")
public class ComplaintView extends VerticalLayout implements BeforeEnterObserver {

    private final ComplaintPresenter presenter;

    private final TextField subjectField = new TextField("Subject");
    private final TextArea messageField = new TextArea("Describe the issue");
    private final Span statusMessage = new Span();
    private final Grid<ComplaintDTO> grid = new Grid<>(ComplaintDTO.class, false);

    public ComplaintView(ComplaintPresenter presenter) {
        this.presenter = presenter;
        initView();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        presenter.loadMyComplaints(this);
    }

    private void initView() {
        add(new Button("<- Home", e -> UI.getCurrent().navigate("")));
        setPadding(true);
        setSpacing(true);

        statusMessage.setVisible(false);
        statusMessage.getStyle().set("font-weight", "bold");

        subjectField.setWidthFull();
        messageField.setWidthFull();
        Button submit = new Button("Submit Complaint", e -> {
            statusMessage.setVisible(false);
            presenter.handleSubmit(subjectField.getValue(), messageField.getValue(), this);
        });

        grid.addColumn(ComplaintDTO::subject).setHeader("Subject").setFlexGrow(1);
        grid.addColumn(ComplaintDTO::message).setHeader("Message").setFlexGrow(2);
        grid.addColumn(ComplaintDTO::status).setHeader("Status").setFlexGrow(0);
        grid.addColumn(c -> c.adminResponse() == null ? "—" : c.adminResponse())
                .setHeader("Admin response").setFlexGrow(2);
        grid.setWidthFull();
        grid.setMaxHeight("400px");

        add(new H2("Complaints"),
                new H3("Submit a complaint"), subjectField, messageField, submit,
                statusMessage,
                new H3("My complaints"), grid);
    }

    public void displayComplaints(List<ComplaintDTO> complaints) {
        grid.setItems(complaints);
    }

    public void clearForm() {
        subjectField.clear();
        messageField.clear();
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
