package com.sadna.group13a.presentation.views.company;

import com.sadna.group13a.application.DTO.EventDTO;
import com.sadna.group13a.application.DTO.VenueMapDTO;
import com.sadna.group13a.application.DTO.ZoneCreationDTO;
import com.sadna.group13a.domain.Aggregates.Event.EventSaleMode;
import com.sadna.group13a.domain.Aggregates.Event.ZoneType;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        add(new Button("<- Home", e -> UI.getCurrent().navigate("")));
        setPadding(true);
        setSpacing(true);

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);
        header.setAlignItems(Alignment.CENTER);
        header.add(new H2("Event Management"),
                new Button("← Back to Dashboard", e -> presenter.handleBack(companyId)));

        statusMessage.setVisible(false);
        statusMessage.getStyle().set("font-weight", "bold");

        // ── Events grid ───────────────────────────────────────────
        eventsGrid.addColumn(EventDTO::title).setHeader("Title").setFlexGrow(2);
        eventsGrid.addColumn(e -> e.artist() != null ? e.artist() : "").setHeader("Artist");
        eventsGrid.addColumn(EventDTO::category).setHeader("Category");
        eventsGrid.addColumn(e -> e.eventDate() != null ? e.eventDate().toLocalDate().toString() : "—").setHeader("Date");
        eventsGrid.addColumn(e -> e.isPublished() ? "Published" : "Draft").setHeader("Status");
        eventsGrid.addColumn(e -> e.isPublished() ? String.valueOf(e.totalAvailableTickets()) : "—")
                .setHeader("Available");
        eventsGrid.addColumn(e -> e.saleMode() != null ? e.saleMode().toString() : "—").setHeader("Type");
        eventsGrid.addComponentColumn(event -> {
            Button editBtn = new Button("Edit", click -> {
                statusMessage.setVisible(false);
                openEditDialog(event);
            });
            Button settingsBtn = new Button("Settings", click -> {
                statusMessage.setVisible(false);
                openEventSettingsDialog(event);
            });
            Button policiesBtn = new Button("Policies", click -> {
                statusMessage.setVisible(false);
                UI.getCurrent().navigate("company/" + companyId + "/event-policies/" + event.id());
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

            HorizontalLayout row1 = new HorizontalLayout(editBtn, settingsBtn, policiesBtn);
            row1.setSpacing(true);
            row1.setPadding(false);

            HorizontalLayout row2 = new HorizontalLayout(toggleBtn);
            row2.setSpacing(true);
            row2.setPadding(false);
            if (!event.isPublished()) {
                Button venueBtn = new Button("Venue", click -> {
                    statusMessage.setVisible(false);
                    openVenueDialog(event);
                });
                row2.addComponentAsFirst(venueBtn);
            } else {
                Button expandBtn = new Button("Expand Venue", click -> {
                    statusMessage.setVisible(false);
                    openExpandVenueDialog(event);
                });
                row2.addComponentAsFirst(expandBtn);
            }

            VerticalLayout actions = new VerticalLayout(row1, row2);
            actions.setPadding(false);
            actions.setSpacing(false);
            return actions;
        }).setHeader("Actions").setAutoWidth(true).setFlexGrow(0);
        eventsGrid.setWidthFull();
        eventsGrid.setMaxHeight("400px");

        Button createBtn = new Button("+ Create Event", e -> openCreateDialog());

        add(header, statusMessage, new H3("Events"), eventsGrid, createBtn);
    }

    // ── Create dialog ─────────────────────────────────────────────

    private void openCreateDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Create New Event");

        TextField titleField     = new TextField("Title");
        TextArea  descField      = new TextArea("Description");
        DateTimePicker dateField = new DateTimePicker("Date & Time");
        dateField.setRequiredIndicatorVisible(true);
        TextField categoryField  = new TextField("Category");
        TextField artistField    = new TextField("Artist");
        TextField locationField  = new TextField("Location");

        titleField.setWidthFull();
        descField.setWidthFull();
        dateField.setWidthFull();

        VerticalLayout form = new VerticalLayout(titleField, descField, dateField, categoryField, artistField, locationField);
        form.setPadding(false);

        Button confirmBtn = new Button("Create", click -> {
            statusMessage.setVisible(false);
            presenter.handleCreateEvent(this, companyId, titleField.getValue(),
                    descField.getValue(), dateField.getValue(),
                    categoryField.getValue(), artistField.getValue(), locationField.getValue());
            dialog.close();
        });
        dialog.add(form);
        dialog.getFooter().add(new Button("Cancel", click -> dialog.close()), confirmBtn);
        dialog.open();
    }

    // ── Edit dialog ───────────────────────────────────────────────

    private void openEditDialog(EventDTO event) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Edit Event");

        TextField titleField     = new TextField("Title");
        TextArea  descField      = new TextArea("Description");
        DateTimePicker dateField = new DateTimePicker("Date & Time");
        TextField categoryField  = new TextField("Category");
        TextField artistField    = new TextField("Artist");

        titleField.setValue(event.title() != null ? event.title() : "");
        descField.setValue(event.description() != null ? event.description() : "");
        dateField.setValue(event.eventDate());
        categoryField.setValue(event.category() != null ? event.category() : "");
        artistField.setValue(event.artist() != null ? event.artist() : "");
        titleField.setWidthFull();
        descField.setWidthFull();
        dateField.setWidthFull();

        VerticalLayout form = new VerticalLayout(titleField, descField, dateField, categoryField, artistField);
        form.setPadding(false);

        Button saveBtn = new Button("Save", click -> {
            statusMessage.setVisible(false);
            presenter.handleUpdateEvent(this, companyId, event.id(),
                    titleField.getValue(), descField.getValue(),
                    dateField.getValue(), categoryField.getValue(), artistField.getValue());
            dialog.close();
        });
        dialog.add(form);
        dialog.getFooter().add(new Button("Cancel", click -> dialog.close()), saveBtn);
        dialog.open();
    }

    // ── Event settings dialog (sale mode + policies) ──────────────

    private void openEventSettingsDialog(EventDTO event) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Event Settings — " + event.title());
        dialog.setWidth("520px");

        // Sale mode
        Select<EventSaleMode> saleModeSelect = new Select<>();
        saleModeSelect.setLabel("Sale Mode");
        saleModeSelect.setItems(EventSaleMode.values());
        saleModeSelect.setWidthFull();

        IntegerField queueCapacityField = new IntegerField("Max concurrent users in queue");
        queueCapacityField.setMin(1);
        queueCapacityField.setValue(50);
        queueCapacityField.setWidthFull();
        queueCapacityField.setVisible(false);

        saleModeSelect.addValueChangeListener(e ->
                queueCapacityField.setVisible(EventSaleMode.QUEUE == e.getValue()));

        Button setSaleModeBtn = new Button("Set Sale Mode", click -> {
            EventSaleMode mode = saleModeSelect.getValue();
            if (mode != null) {
                Integer queueCap = mode == EventSaleMode.QUEUE ? queueCapacityField.getValue() : null;
                presenter.handleSetSaleMode(this, companyId, event.id(), mode, queueCap);
                dialog.close();
            }
        });

        VerticalLayout content = new VerticalLayout(
                new H3("Sale Mode"), saleModeSelect, queueCapacityField, setSaleModeBtn
        );
        content.setPadding(false);

        dialog.add(content);
        dialog.getFooter().add(new Button("Close", click -> dialog.close()));
        dialog.open();
    }

    // ── Venue dialog (with prefill) ───────────────────────────────

    private void openVenueDialog(EventDTO event) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Configure Venue — " + event.title());
        dialog.setWidth("640px");

        TextField venueNameField = new TextField("Venue name");
        venueNameField.setWidthFull();

        VerticalLayout zonesContainer = new VerticalLayout();
        zonesContainer.setPadding(false);
        List<ZoneRow> zoneRows = new ArrayList<>();

        // Prefill with existing venue map if one exists
        VenueMapDTO existing = presenter.loadVenueMap(event.id());
        if (existing != null) {
            venueNameField.setValue(existing.getVenueName() != null ? existing.getVenueName() : "");
            if (existing.getZones() != null) {
                for (var zone : existing.getZones()) {
                    addZoneRow(zonesContainer, zoneRows, zone.getName(), zone.getType(), zone.getBasePrice(),
                               zone.getCapacity(), zone.getRows() > 0 ? zone.getRows() : null,
                               zone.getColumns() > 0 ? zone.getColumns() : null);
                }
            }
        }
        if (zoneRows.isEmpty()) {
            addZoneRow(zonesContainer, zoneRows, null, null, null, null, null, null);
        }

        Button addZoneBtn = new Button("+ Add Zone", e -> addZoneRow(zonesContainer, zoneRows, null, null, null, null, null, null));

        Button saveBtn = new Button("Save", click -> {
            statusMessage.setVisible(false);
            List<ZoneCreationDTO> zones = new ArrayList<>();
            for (ZoneRow row : zoneRows) {
                if (!row.isBlank()) zones.add(row.toDTO());
            }
            presenter.handleConfigureVenue(this, companyId, event.id(), venueNameField.getValue(), zones);
            dialog.close();
        });

        VerticalLayout form = new VerticalLayout(venueNameField, new H3("Zones"), zonesContainer, addZoneBtn);
        form.setPadding(false);

        dialog.add(form);
        dialog.getFooter().add(new Button("Cancel", click -> dialog.close()), saveBtn);
        dialog.open();
    }

    // ── Expand venue dialog (published events only) ───────────────

    private void openExpandVenueDialog(EventDTO event) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Expand Venue — " + event.title());
        dialog.setWidth("680px");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);

        // ── Section 1: add to existing zones ─────────────────────
        VenueMapDTO existing = presenter.loadVenueMap(event.id());
        Map<String, IntegerField> addCountByZoneId = new LinkedHashMap<>();

        if (existing != null && existing.getZones() != null && !existing.getZones().isEmpty()) {
            content.add(new H3("Add seats to existing zones"));
            for (var zone : existing.getZones()) {
                IntegerField addField = new IntegerField();
                addField.setMin(0);
                addField.setValue(0);
                addField.setWidth("100px");
                addCountByZoneId.put(zone.getId(), addField);

                String label = zone.getName() + "  [" + zone.getType() + ", capacity: " + zone.getCapacity() + "]";
                HorizontalLayout row = new HorizontalLayout(new Span(label), addField);
                row.setAlignItems(com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.CENTER);
                row.setWidthFull();
                content.add(row);
            }
        }

        // ── Section 2: add new zones ──────────────────────────────
        content.add(new H3("Add new zones"));
        VerticalLayout newZonesContainer = new VerticalLayout();
        newZonesContainer.setPadding(false);
        List<ZoneRow> newZoneRows = new ArrayList<>();

        Button addZoneBtn = new Button("+ Add Zone",
                e -> addZoneRow(newZonesContainer, newZoneRows, null, null, null, null, null, null));
        content.add(newZonesContainer, addZoneBtn);

        dialog.add(content);

        Button saveBtn = new Button("Save", click -> {
            statusMessage.setVisible(false);
            Map<String, Integer> additions = new LinkedHashMap<>();
            for (Map.Entry<String, IntegerField> entry : addCountByZoneId.entrySet()) {
                Integer val = entry.getValue().getValue();
                if (val != null && val > 0) {
                    additions.put(entry.getKey(), val);
                }
            }
            List<ZoneCreationDTO> newZones = new ArrayList<>();
            for (ZoneRow row : newZoneRows) {
                if (!row.isBlank()) newZones.add(row.toDTO());
            }
            presenter.handleExpandVenue(this, companyId, event.id(), additions, newZones);
            dialog.close();
        });

        dialog.getFooter().add(new Button("Cancel", click -> dialog.close()), saveBtn);
        dialog.open();
    }

    private void addZoneRow(VerticalLayout container, List<ZoneRow> rows,
                             String name, ZoneType type, Double price, Integer capacity,
                             Integer rowCount, Integer columnCount) {
        ZoneRow row = new ZoneRow(name, type, price, capacity, rowCount, columnCount);
        row.removeBtn.addClickListener(e -> {
            container.remove(row.layout);
            rows.remove(row);
        });
        rows.add(row);
        container.add(row.layout);
    }

    private static final class ZoneRow {
        final TextField name           = new TextField("Zone name");
        final Select<ZoneType> type    = new Select<>();
        final NumberField price        = new NumberField("Price");
        final IntegerField capacity    = new IntegerField("Capacity");
        final IntegerField rowsField   = new IntegerField("Rows");
        final IntegerField colsField   = new IntegerField("Columns");
        final Button removeBtn         = new Button("✕");
        final HorizontalLayout layout  = new HorizontalLayout();

        ZoneRow(String nameVal, ZoneType typeVal, Double priceVal, Integer capacityVal,
                Integer rowCount, Integer columnCount) {
            type.setLabel("Type");
            type.setItems(ZoneType.SEATED, ZoneType.STANDING);
            ZoneType initialType = typeVal != null ? typeVal : ZoneType.SEATED;
            type.setValue(initialType);

            price.setMin(0);
            capacity.setMin(1);
            rowsField.setMin(1);
            colsField.setMin(1);

            if (nameVal != null) name.setValue(nameVal);
            if (priceVal != null) price.setValue(priceVal);
            if (capacityVal != null) capacity.setValue(capacityVal);
            if (rowCount != null) rowsField.setValue(rowCount);
            if (columnCount != null) colsField.setValue(columnCount);

            applyTypeLayout(initialType);
            type.addValueChangeListener(e -> applyTypeLayout(e.getValue()));

            layout.setAlignItems(com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.BASELINE);
            layout.add(name, type, price, rowsField, colsField, capacity, removeBtn);
        }

        private void applyTypeLayout(ZoneType t) {
            boolean seated = t == ZoneType.SEATED;
            rowsField.setVisible(seated);
            colsField.setVisible(seated);
            capacity.setVisible(!seated);
        }

        boolean isBlank() { return name.getValue() == null || name.getValue().isBlank(); }

        ZoneCreationDTO toDTO() {
            double basePrice = price.getValue() != null ? price.getValue() : 0.0;
            if (type.getValue() == ZoneType.SEATED) {
                int rows = rowsField.getValue() != null ? rowsField.getValue() : 0;
                int cols = colsField.getValue() != null ? colsField.getValue() : 0;
                return new ZoneCreationDTO(name.getValue().trim(), ZoneType.SEATED, basePrice, rows * cols, rows, cols);
            } else {
                int cap = capacity.getValue() != null ? capacity.getValue() : 0;
                return new ZoneCreationDTO(name.getValue().trim(), ZoneType.STANDING, basePrice, cap, 0, 0);
            }
        }
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
