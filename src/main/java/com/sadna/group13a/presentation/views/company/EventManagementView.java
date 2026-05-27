package com.sadna.group13a.presentation.views.company;

import com.sadna.group13a.application.DTO.EventDTO;
import com.sadna.group13a.application.DTO.VenueMapDTO;
import com.sadna.group13a.application.DTO.ZoneCreationDTO;
import com.sadna.group13a.domain.Aggregates.Event.EventSaleMode;
import com.sadna.group13a.domain.Aggregates.Event.ZoneType;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datepicker.DatePicker;
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

import java.time.LocalDate;
import java.util.ArrayList;
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
        eventsGrid.addColumn(EventDTO::category).setHeader("Category");
        eventsGrid.addColumn(e -> e.eventDate().toLocalDate().toString()).setHeader("Date");
        eventsGrid.addColumn(e -> e.isPublished() ? "Published" : "Draft").setHeader("Status");
        eventsGrid.addColumn(e -> e.isPublished() ? String.valueOf(e.totalAvailableTickets()) : "—")
                .setHeader("Available");
        eventsGrid.addComponentColumn(event -> {
            Button editBtn = new Button("Edit", click -> {
                statusMessage.setVisible(false);
                openEditDialog(event);
            });
            Button settingsBtn = new Button("Settings", click -> {
                statusMessage.setVisible(false);
                openEventSettingsDialog(event);
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

            HorizontalLayout row1 = new HorizontalLayout(editBtn, settingsBtn);
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
        TextField categoryField  = new TextField("Category");
        TextField locationField  = new TextField("Location");

        titleField.setWidthFull();
        descField.setWidthFull();
        dateField.setWidthFull();

        VerticalLayout form = new VerticalLayout(titleField, descField, dateField, categoryField, locationField);
        form.setPadding(false);

        Button confirmBtn = new Button("Create", click -> {
            statusMessage.setVisible(false);
            presenter.handleCreateEvent(this, companyId, titleField.getValue(),
                    descField.getValue(), dateField.getValue(),
                    categoryField.getValue(), locationField.getValue());
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

        titleField.setValue(event.title() != null ? event.title() : "");
        descField.setValue(event.description() != null ? event.description() : "");
        dateField.setValue(event.eventDate());
        categoryField.setValue(event.category() != null ? event.category() : "");
        titleField.setWidthFull();
        descField.setWidthFull();
        dateField.setWidthFull();

        VerticalLayout form = new VerticalLayout(titleField, descField, dateField, categoryField);
        form.setPadding(false);

        Button saveBtn = new Button("Save", click -> {
            statusMessage.setVisible(false);
            presenter.handleUpdateEvent(this, companyId, event.id(),
                    titleField.getValue(), descField.getValue(),
                    dateField.getValue(), categoryField.getValue());
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

        // Purchase policy
        Select<String> purchasePolicySelect = new Select<>();
        purchasePolicySelect.setLabel("Purchase Policy Type");
        purchasePolicySelect.setItems("Allow All", "Max Tickets", "Min Tickets", "Age Restriction");
        purchasePolicySelect.setWidthFull();

        IntegerField policyParamField = new IntegerField();
        policyParamField.setMin(1);
        policyParamField.setWidthFull();
        policyParamField.setVisible(false);

        purchasePolicySelect.addValueChangeListener(e -> {
            String val = e.getValue();
            boolean needsParam = "Max Tickets".equals(val) || "Min Tickets".equals(val) || "Age Restriction".equals(val);
            policyParamField.setVisible(needsParam);
            if ("Max Tickets".equals(val)) policyParamField.setLabel("Max tickets per order");
            else if ("Min Tickets".equals(val)) policyParamField.setLabel("Min tickets per order");
            else if ("Age Restriction".equals(val)) policyParamField.setLabel("Minimum age");
        });

        Button setPurchasePolicyBtn = new Button("Set Purchase Policy", click -> {
            String type = purchasePolicySelect.getValue();
            if (type == null) return;
            switch (type) {
                case "Allow All" -> presenter.handleSetPurchasePolicyAllowAll(this, companyId, event.id());
                case "Max Tickets" -> {
                    if (policyParamField.getValue() != null)
                        presenter.handleSetPurchasePolicyMaxTickets(this, companyId, event.id(), policyParamField.getValue());
                }
                case "Min Tickets" -> {
                    if (policyParamField.getValue() != null)
                        presenter.handleSetPurchasePolicyMinTickets(this, companyId, event.id(), policyParamField.getValue());
                }
                case "Age Restriction" -> {
                    if (policyParamField.getValue() != null)
                        presenter.handleSetPurchasePolicyAgeRestriction(this, companyId, event.id(), policyParamField.getValue());
                }
            }
            dialog.close();
        });

        // Discount policy
        Select<String> discountPolicySelect = new Select<>();
        discountPolicySelect.setLabel("Discount Policy Type");
        discountPolicySelect.setItems("No Discount", "Simple Discount (%)");
        discountPolicySelect.setWidthFull();

        NumberField discountPctField  = new NumberField("Percentage (0–100)");
        DatePicker  discountStartDate = new DatePicker("Start Date");
        DatePicker  discountEndDate   = new DatePicker("End Date");
        discountPctField.setMin(0); discountPctField.setMax(100);
        discountPctField.setWidthFull();
        VerticalLayout discountParams = new VerticalLayout(discountPctField, discountStartDate, discountEndDate);
        discountParams.setPadding(false);
        discountParams.setVisible(false);

        discountPolicySelect.addValueChangeListener(e ->
                discountParams.setVisible("Simple Discount (%)".equals(e.getValue())));

        Button setDiscountPolicyBtn = new Button("Set Discount Policy", click -> {
            String type = discountPolicySelect.getValue();
            if (type == null) return;
            if ("No Discount".equals(type)) {
                presenter.handleSetDiscountPolicyNone(this, companyId, event.id());
            } else if ("Simple Discount (%)".equals(type)) {
                LocalDate start = discountStartDate.getValue();
                LocalDate end   = discountEndDate.getValue();
                Double pct      = discountPctField.getValue();
                if (pct != null && start != null && end != null)
                    presenter.handleSetDiscountPolicySimple(this, companyId, event.id(), pct, start, end);
            }
            dialog.close();
        });

        VerticalLayout content = new VerticalLayout(
                new H3("Sale Mode"), saleModeSelect, queueCapacityField, setSaleModeBtn,
                new H3("Purchase Policy"), purchasePolicySelect, policyParamField, setPurchasePolicyBtn,
                new H3("Discount Policy"), discountPolicySelect, discountParams, setDiscountPolicyBtn
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
                    addZoneRow(zonesContainer, zoneRows, zone.getName(), zone.getType(), zone.getBasePrice(), zone.getCapacity());
                }
            }
        }
        if (zoneRows.isEmpty()) {
            addZoneRow(zonesContainer, zoneRows, null, null, null, null);
        }

        Button addZoneBtn = new Button("+ Add Zone", e -> addZoneRow(zonesContainer, zoneRows, null, null, null, null));

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

    private void addZoneRow(VerticalLayout container, List<ZoneRow> rows,
                             String name, ZoneType type, Double price, Integer capacity) {
        ZoneRow row = new ZoneRow(name, type, price, capacity);
        row.removeBtn.addClickListener(e -> {
            container.remove(row.layout);
            rows.remove(row);
        });
        rows.add(row);
        container.add(row.layout);
    }

    private static final class ZoneRow {
        final TextField name         = new TextField("Zone name");
        final Select<ZoneType> type  = new Select<>();
        final NumberField price      = new NumberField("Price");
        final IntegerField capacity  = new IntegerField("Seats / capacity");
        final Button removeBtn       = new Button("✕");
        final HorizontalLayout layout = new HorizontalLayout();

        ZoneRow(String nameVal, ZoneType typeVal, Double priceVal, Integer capacityVal) {
            type.setLabel("Type");
            type.setItems(ZoneType.SEATED, ZoneType.STANDING);
            type.setValue(typeVal != null ? typeVal : ZoneType.SEATED);
            price.setMin(0);
            capacity.setMin(1);
            if (nameVal != null) name.setValue(nameVal);
            if (priceVal != null) price.setValue(priceVal);
            if (capacityVal != null) capacity.setValue(capacityVal);
            layout.add(name, type, price, capacity, removeBtn);
        }

        boolean isBlank() { return name.getValue() == null || name.getValue().isBlank(); }

        ZoneCreationDTO toDTO() {
            double basePrice = price.getValue() != null ? price.getValue() : 0.0;
            int cap = capacity.getValue() != null ? capacity.getValue() : 0;
            return new ZoneCreationDTO(name.getValue().trim(), type.getValue(), basePrice, cap);
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
