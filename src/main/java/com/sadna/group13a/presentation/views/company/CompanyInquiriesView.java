package com.sadna.group13a.presentation.views.company;

import com.sadna.group13a.application.DTO.InquiryDTO;
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

/** Owner view to read and answer buyer inquiries to the company (II.4.4). */
@Route("company/:companyId/inquiries")
@PageTitle("Company Inquiries")
public class CompanyInquiriesView extends VerticalLayout implements BeforeEnterObserver {

    private final CompanyInquiriesPresenter presenter;
    private String companyId;

    private final Span statusMessage = new Span();
    private final Grid<InquiryDTO> grid = new Grid<>(InquiryDTO.class, false);
    private final TextArea responseField = new TextArea("Reply");

    private InquiryDTO selected;

    public CompanyInquiriesView(CompanyInquiriesPresenter presenter) {
        this.presenter = presenter;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        companyId = event.getRouteParameters().get("companyId").orElse("");
        initView();
        presenter.loadInquiries(this, companyId);
    }

    private void initView() {
        removeAll();
        add(new Button("<- Home", e -> UI.getCurrent().navigate("")));
        setPadding(true);
        setSpacing(true);

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);
        header.setAlignItems(Alignment.CENTER);
        header.add(new H2("Company Inquiries"),
                new Button("← Back to Dashboard", e -> presenter.handleBack(companyId)));

        statusMessage.setVisible(false);
        statusMessage.getStyle().set("font-weight", "bold");

        grid.addColumn(InquiryDTO::fromUsername).setHeader("From").setFlexGrow(0);
        grid.addColumn(InquiryDTO::message).setHeader("Message").setFlexGrow(2);
        grid.addColumn(InquiryDTO::status).setHeader("Status").setFlexGrow(0);
        grid.addColumn(i -> i.response() == null ? "—" : i.response())
                .setHeader("Reply").setFlexGrow(2);
        grid.setWidthFull();
        grid.setMaxHeight("400px");
        grid.asSingleSelect().addValueChangeListener(e -> selected = e.getValue());

        responseField.setWidthFull();
        Button respondBtn = new Button("Send Reply", e -> {
            statusMessage.setVisible(false);
            if (selected == null) {
                showError("Select an inquiry first.");
                return;
            }
            presenter.handleRespond(selected.id(), responseField.getValue(), companyId, this);
            responseField.clear();
        });
        HorizontalLayout respondRow = new HorizontalLayout(responseField, respondBtn);
        respondRow.setAlignItems(Alignment.BASELINE);
        respondRow.setWidthFull();

        add(header, statusMessage, grid, new H3("Reply"), respondRow);
    }

    public void displayInquiries(List<InquiryDTO> inquiries) {
        grid.setItems(inquiries);
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
