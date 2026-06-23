package com.sadna.group13a.presentation.views.event;

import com.sadna.group13a.application.DTO.EventDTO;
import com.sadna.group13a.application.DTO.RaffleDTO;
import com.sadna.group13a.application.DTO.SeatDTO;
import com.sadna.group13a.application.DTO.VenueMapDTO;
import com.sadna.group13a.application.DTO.ZoneDTO;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.domain.Aggregates.Event.EventSaleMode;
import com.sadna.group13a.domain.Aggregates.Raffle.RaffleStatus;
import com.sadna.group13a.domain.Aggregates.Event.SeatStatus;
import com.sadna.group13a.domain.Aggregates.Event.ZoneType;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;

import java.time.format.DateTimeFormatter;

@Route("events/:eventId")
@PageTitle("Event")
public class EventDetailView extends VerticalLayout implements BeforeEnterObserver {

    private final EventDetailPresenter presenter;
    private String eventId;

    private final Span errorMessage = new Span();
    private final Span successMessage = new Span();

    public EventDetailView(EventDetailPresenter presenter) {
        this.presenter = presenter;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        eventId = event.getRouteParameters().get("eventId").orElse("");
        initView();
    }

    private void initView() {
        removeAll();
        setPadding(true);
        setSpacing(true);

        errorMessage.getStyle().set("color", "var(--lumo-error-color)");
        errorMessage.setVisible(false);
        successMessage.getStyle().set("color", "var(--lumo-success-color)");
        successMessage.setVisible(false);

        Button backButton = new Button("← Back", e -> UI.getCurrent().navigate(""));
        add(backButton, errorMessage, successMessage);

        if (eventId.isBlank()) {
            showError("Invalid event.");
            return;
        }

        String token = (String) VaadinSession.getCurrent().getAttribute("token");

        Result<EventDTO> eventResult = presenter.loadEvent(token, eventId);
        if (!eventResult.isSuccess() && token != null) {
            // stale/invalid token blocked anonymous access — clear it and retry
            VaadinSession.getCurrent().setAttribute("token", null);
            token = null;
            eventResult = presenter.loadEvent(null, eventId);
        }
        if (!eventResult.isSuccess()) {
            showError(eventResult.getErrorMessage());
            return;
        }

        EventDTO event = eventResult.getOrThrow();
        if (!event.isPublished() && token == null) {
            showError("Event not available.");
            return;
        }
        renderEventInfo(event);

        if (token == null) {
            add(new Paragraph("Log in to view seating and purchase tickets."));
            return;
        }

        if (event.saleMode() == EventSaleMode.QUEUE) {
            renderQueueGate(token, event);
        } else if (event.saleMode() == EventSaleMode.RAFFLE) {
            renderRaffleGate(token);
        } else {
            renderVenueSection(token);
        }
    }

    private void renderEventInfo(EventDTO event) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");
        String saleModeLabel = event.saleMode() != null ? event.saleMode().name() : "REGULAR";
        add(
            new H2(event.title()),
            new Paragraph("Artist: " + (event.artist() != null ? event.artist() : "TBD")),
            new Paragraph("Date: " + (event.eventDate() != null ? event.eventDate().format(fmt) : "TBD")),
            new Paragraph("Location: " + (event.location() != null ? event.location() : "TBD")),
            new Paragraph("Category: " + event.category()),
            new Paragraph("Sale type: " + saleModeLabel),
            new Paragraph(event.description()),
            new Paragraph("Available tickets: " + event.totalAvailableTickets())
        );
    }

    private void renderQueueGate(String token, EventDTO event) {
        if (presenter.isUserActiveInQueue(token, eventId)) {
            // User holds an active slot — show the normal purchase UI
            renderVenueSection(token);
        } else {
            // User has not yet joined or is still waiting
            Paragraph info = new Paragraph(
                "This event uses a virtual queue. Join the queue and wait for your turn before purchasing.");
            Button joinBtn = new Button("Join Queue →",
                e -> UI.getCurrent().navigate("queue/" + eventId));
            joinBtn.getStyle().set("font-size", "1rem");
            add(info, joinBtn);
        }
    }

    private void renderRaffleGate(String token) {
        Result<RaffleDTO> raffleResult = presenter.getRaffleForEvent(token, eventId);

        if (!raffleResult.isSuccess()) {
            add(new Paragraph("This event uses a raffle system. No raffle is currently active — check back soon."));
            return;
        }

        RaffleDTO raffle = raffleResult.getOrThrow();

        if (raffle.status() == RaffleStatus.OPEN_FOR_REGISTRATION) {
            Paragraph info = new Paragraph(
                "This event uses a raffle. Register for the raffle for a chance to buy tickets.");
            Button joinBtn = new Button("Join Raffle", e -> {
                boolean joined = presenter.joinRaffle(token, raffle.id(), this);
                if (joined) {
                    e.getSource().setEnabled(false);
                    e.getSource().setText("Joined!");
                }
            });
            add(info, joinBtn);

        } else if (raffle.status() == RaffleStatus.DRAWN) {
            if (presenter.hasWonRaffle(token, eventId)) {
                renderVenueSection(token);
            } else {
                add(new Paragraph("The raffle for this event has concluded."),
                    new Paragraph("You did not win this raffle, or you did not participate."));
            }

        } else {
            add(new Paragraph("The raffle for this event has been closed."));
        }
    }

    private void renderVenueSection(String token) {
        Result<VenueMapDTO> venueResult = presenter.loadVenueMap(token, eventId);
        if (venueResult.isSuccess()) {
            renderVenueMap(venueResult.getOrThrow(), token);
        } else {
            add(new Paragraph("Venue map not available: " + venueResult.getErrorMessage()));
        }
    }

    private void renderVenueMap(VenueMapDTO venueMap, String token) {
        add(new H3("Venue: " + venueMap.getVenueName()));
        for (ZoneDTO zone : venueMap.getZones()) {
            add(buildZoneSection(zone, token));
        }
    }

    private VerticalLayout buildZoneSection(ZoneDTO zone, String token) {
        VerticalLayout section = new VerticalLayout();
        section.getStyle()
            .set("border", "1px solid var(--lumo-contrast-20pct)")
            .set("border-radius", "var(--lumo-border-radius-m)")
            .set("padding", "1em");

        String typeLabel = zone.getType() == ZoneType.SEATED ? "Seated" : "Standing";
        H3 header = new H3(zone.getName() + " (" + typeLabel + ")");
        header.getStyle().set("margin-top", "0");
        section.add(header);
        section.add(new Paragraph(String.format("Price: $%.2f  |  Available: %d / %d",
                zone.getBasePrice(), zone.getAvailable(), zone.getCapacity())));

        if (zone.getType() == ZoneType.SEATED && zone.getRows() > 0 && zone.getColumns() > 0) {
            section.add(buildTheaterGrid(zone, token));
        } else if (zone.getType() == ZoneType.SEATED) {
            section.add(buildSeatedContent(zone, token));
        } else {
            section.add(buildStandingContent(zone, token));
        }
        return section;
    }

    private Div buildTheaterGrid(ZoneDTO zone, String token) {
        Div wrapper = new Div();

        // Stage bar
        Div stageBar = new Div(new Span("★  STAGE  ★"));
        stageBar.getStyle()
            .set("background", "var(--lumo-contrast-10pct)")
            .set("text-align", "center")
            .set("padding", "0.4em 1em")
            .set("border-radius", "var(--lumo-border-radius-m) var(--lumo-border-radius-m) 0 0")
            .set("font-weight", "600")
            .set("letter-spacing", "0.15em")
            .set("color", "var(--lumo-secondary-text-color)")
            .set("margin-bottom", "8px")
            .set("width", "100%");
        wrapper.add(stageBar);

        // Seat grid — seats are already ordered A1, A2 … by VenueMapFactory
        Div grid = new Div();
        grid.getStyle()
            .set("display", "grid")
            .set("grid-template-columns", "repeat(" + zone.getColumns() + ", auto)")
            .set("gap", "4px")
            .set("justify-content", "start");

        if (zone.getSeats() != null) {
            for (SeatDTO seat : zone.getSeats()) {
                Button btn = new Button(seat.getLabel());
                btn.getStyle().set("min-width", "3em").set("padding", "0.2em 0.4em");
                if (seat.getStatus() == SeatStatus.AVAILABLE) {
                    btn.addClickListener(e ->
                        presenter.addSeatedTicket(token, eventId, zone.getId(), seat.getId(), this)
                    );
                } else {
                    btn.setEnabled(false);
                    btn.getStyle().set("opacity", "0.4");
                }
                grid.add(btn);
            }
        }
        wrapper.add(grid);
        return wrapper;
    }

    private FlexLayout buildSeatedContent(ZoneDTO zone, String token) {
        FlexLayout grid = new FlexLayout();
        grid.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        grid.getStyle().set("gap", "4px");

        if (zone.getSeats() != null) {
            for (SeatDTO seat : zone.getSeats()) {
                Button btn = new Button(seat.getLabel());
                if (seat.getStatus() == SeatStatus.AVAILABLE) {
                    btn.addClickListener(e ->
                        presenter.addSeatedTicket(token, eventId, zone.getId(), seat.getId(), this)
                    );
                } else {
                    btn.setEnabled(false);
                    btn.getStyle().set("opacity", "0.4");
                }
                grid.add(btn);
            }
        }
        return grid;
    }

    private HorizontalLayout buildStandingContent(ZoneDTO zone, String token) {
        HorizontalLayout row = new HorizontalLayout();
        row.setAlignItems(FlexComponent.Alignment.BASELINE);

        IntegerField qty = new IntegerField("Quantity");
        Button addBtn = new Button("Add to Cart");

        if (zone.getAvailable() <= 0) {
            qty.setEnabled(false);
            addBtn.setEnabled(false);
            addBtn.setText("Sold Out");
        } else {
            qty.setMin(1);
            qty.setMax(zone.getAvailable());
            qty.setValue(1);
            addBtn.addClickListener(e -> {
                int quantity = qty.getValue() != null ? qty.getValue() : 1;
                presenter.addStandingTickets(token, eventId, zone.getId(), quantity, this);
            });
        }

        row.add(qty, addBtn);
        return row;
    }

    public void showError(String message) {
        successMessage.setVisible(false);
        errorMessage.setText(message);
        errorMessage.setVisible(true);
    }

    public void showSuccess(String message) {
        errorMessage.setVisible(false);
        successMessage.setText(message);
        successMessage.setVisible(true);
    }
}
