package com.sadna.group13a.presentation.views.company;

import com.sadna.group13a.application.DTO.EventDTO;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.util.List;

@Route("company/:companyId/events")
@PageTitle("Event Management")
public class EventManagementView extends VerticalLayout implements BeforeEnterObserver {

    private final EventManagementPresenter presenter;
    private String companyId;

    private final Span statusMessage = new Span();
    private final Grid<EventDTO> eventsGrid = new Grid<>(EventDTO.class, false);

    public EventManagementView(EventManagementPresenter presenter) {
        this.presenter = presenter;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        companyId = event.getRouteParameters().get("companyId").orElse("");
        initView();
        presenter.loadEvents(this, companyId);
    }

    private void initView() {
        removeAll();
        setPadding(true);
        setSpacing(true);

        // ── Header ────────────────────────────────────────────────
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);
        header.setAlignItems(Alignment.CENTER);
        Button backBtn = new Button("← Back to Dashboard", e -> presenter.handleBack(companyId));
        header.add(new H2("Event Management"), backBtn);

        // ── Status message ────────────────────────────────────────
        statusMessage.setVisible(false);
        statusMessage.getStyle().set("font-weight", "bold");

        // ── Events grid ───────────────────────────────────────────
        eventsGrid.addColumn(EventDTO::title).setHeader("Title").setFlexGrow(2);
        eventsGrid.addColumn(EventDTO::category).setHeader("Category").setFlexGrow(1);
        eventsGrid.addColumn(EventDTO::location).setHeader("Location").setFlexGrow(1);
        eventsGrid.addColumn(e -> e.eventDate().toLocalDate().toString()).setHeader("Date");
        eventsGrid.addColumn(e -> e.isPublished() ? "Published" : "Draft").setHeader("Status");
        eventsGrid.addColumn(e -> e.isPublished() ? String.valueOf(e.totalAvailableTickets()) : "—")
                .setHeader("Available Tickets");
        eventsGrid.addComponentColumn(event -> {
            Button editBtn = new Button("Edit", click -> {
                statusMessage.setVisible(false);
                openEditDialog(event);
            });
            Button toggleBtn = event.isPublished()
                    ? new Button("Unpublish", click -> {
                        statusMessage.setVisible(false);
                        presenter.handleUnpublishEvent(this, companyId, event.id());
                    })
                    : new Button("Publish", click -> {
                        statusMessage.setVisible(false);
                        presenter.handlePublishEvent(this, companyId, event.id());
                    });
            HorizontalLayout actions = new HorizontalLayout(editBtn, toggleBtn);
            actions.setSpacing(true);
            return actions;
        }).setHeader("Actions");
        eventsGrid.setWidthFull();
        eventsGrid.setMaxHeight("400px");

        // ── Create event button ───────────────────────────────────
        Button createBtn = new Button("+ Create Event", e -> openCreateDialog());

        add(
                header,
                statusMessage,
                new H3("Events"), eventsGrid,
                createBtn
        );
    }

    private void openCreateDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Create New Event");

        TextField titleField       = new TextField("Title");
        TextArea  descField        = new TextArea("Description");
        DateTimePicker dateField   = new DateTimePicker("Date & Time");
        TextField categoryField    = new TextField("Category");
        TextField locationField    = new TextField("Location");

        titleField.setWidthFull();
        descField.setWidthFull();
        dateField.setWidthFull();

        VerticalLayout form = new VerticalLayout(titleField, descField, dateField, categoryField, locationField);
        form.setPadding(false);
        form.setSpacing(true);

        Button confirmBtn = new Button("Create", click -> {
            statusMessage.setVisible(false);
            presenter.handleCreateEvent(
                    this, companyId,
                    titleField.getValue(),
                    descField.getValue(),
                    dateField.getValue(),
                    categoryField.getValue(),
                    locationField.getValue()
            );
            dialog.close();
        });
        Button cancelBtn = new Button("Cancel", click -> dialog.close());

        dialog.add(form);
        dialog.getFooter().add(cancelBtn, confirmBtn);
        dialog.open();
    }

    private void openEditDialog(EventDTO event) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Edit Event");

        TextField titleField     = new TextField("Title");
        TextArea  descField      = new TextArea("Description");
        DateTimePicker dateField = new DateTimePicker("Date & Time");
        TextField categoryField  = new TextField("Category");

        titleField.setValue(event.title() != null ? event.title() : "");
        descField.setValue(event.description() != null ? event.description() : "");
        dateField.setValue(event.eventDate());
        categoryField.setValue(event.category() != null ? event.category() : "");

        titleField.setWidthFull();
        descField.setWidthFull();
        dateField.setWidthFull();

        VerticalLayout form = new VerticalLayout(titleField, descField, dateField, categoryField);
        form.setPadding(false);
        form.setSpacing(true);

        Button saveBtn   = new Button("Save", click -> {
            statusMessage.setVisible(false);
            presenter.handleUpdateEvent(
                    this, companyId, event.id(),
                    titleField.getValue(),
                    descField.getValue(),
                    dateField.getValue(),
                    categoryField.getValue()
            );
            dialog.close();
        });
        Button cancelBtn = new Button("Cancel", click -> dialog.close());

        dialog.add(form);
        dialog.getFooter().add(cancelBtn, saveBtn);
        dialog.open();
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

    public void displayEvents(List<EventDTO> events) {
        eventsGrid.setItems(events);
    }
}
