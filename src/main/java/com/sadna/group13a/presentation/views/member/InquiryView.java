package com.sadna.group13a.presentation.views.member;

import com.sadna.group13a.application.DTO.InquiryDTO;
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

/** Member view to send an inquiry to a company and track responses (II.3.7). */
@Route("member/inquiries")
@PageTitle("My Inquiries")
public class InquiryView extends VerticalLayout implements BeforeEnterObserver {

    private final InquiryPresenter presenter;

    private final TextField companyIdField = new TextField("Company ID");
    private final TextArea messageField = new TextArea("Your message");
    private final Span statusMessage = new Span();
    private final Grid<InquiryDTO> grid = new Grid<>(InquiryDTO.class, false);

    public InquiryView(InquiryPresenter presenter) {
        this.presenter = presenter;
        initView();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        presenter.loadMyInquiries(this);
    }

    private void initView() {
        add(new Button("<- Home", e -> UI.getCurrent().navigate("")));
        setPadding(true);
        setSpacing(true);

        statusMessage.setVisible(false);
        statusMessage.getStyle().set("font-weight", "bold");

        companyIdField.setWidthFull();
        messageField.setWidthFull();
        Button send = new Button("Send Inquiry", e -> {
            statusMessage.setVisible(false);
            presenter.handleSend(companyIdField.getValue(), messageField.getValue(), this);
        });

        grid.addColumn(InquiryDTO::companyId).setHeader("Company").setFlexGrow(0);
        grid.addColumn(InquiryDTO::message).setHeader("Message").setFlexGrow(2);
        grid.addColumn(InquiryDTO::status).setHeader("Status").setFlexGrow(0);
        grid.addColumn(i -> i.response() == null ? "—" : i.response())
                .setHeader("Response").setFlexGrow(2);
        grid.setWidthFull();
        grid.setMaxHeight("400px");

        add(new H2("Inquiries"),
                new H3("Ask a company"), companyIdField, messageField, send,
                statusMessage,
                new H3("My inquiries"), grid);
    }

    public void displayInquiries(List<InquiryDTO> inquiries) {
        grid.setItems(inquiries);
    }

    public void clearForm() {
        companyIdField.clear();
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
