package com.sadna.group13a.presentation.views.company;

import com.sadna.group13a.application.DTO.EventDTO;
import com.sadna.group13a.application.DTO.VenueMapDTO;
import com.sadna.group13a.application.DTO.ZoneCreationDTO;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.EventService;
import com.sadna.group13a.application.Services.QueueService;
import com.sadna.group13a.application.Services.RaffleService;
import com.sadna.group13a.application.config.SystemInitProperties;
import com.sadna.group13a.domain.Aggregates.Event.EventSaleMode;
import com.sadna.group13a.domain.policies.discount.NoDiscountPolicy;
import com.sadna.group13a.domain.policies.discount.SimpleDiscount;
import com.sadna.group13a.domain.policies.purchase.AgeRestrictionPolicy;
import com.sadna.group13a.domain.policies.purchase.AllowAllPolicy;
import com.sadna.group13a.domain.policies.purchase.MaxTicketsPolicy;
import com.sadna.group13a.domain.policies.purchase.MinTicketsPolicy;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class EventManagementPresenter {

    private final EventService eventService;
    private final QueueService queueService;
    private final RaffleService raffleService;
    private final SystemInitProperties initProperties;

    public EventManagementPresenter(EventService eventService, QueueService queueService,
                                    RaffleService raffleService, SystemInitProperties initProperties) {
        this.eventService = eventService;
        this.queueService = queueService;
        this.raffleService = raffleService;
        this.initProperties = initProperties;
    }

    private String getToken() {
        return (String) VaadinSession.getCurrent().getAttribute("token");
    }

    public void loadEvents(EventManagementView view, String companyId) {
        String token = getToken();
        if (token == null) { UI.getCurrent().navigate("login"); return; }
        Result<List<EventDTO>> result = eventService.getCompanyEvents(token, companyId);
        if (result.isSuccess()) {
            view.displayEvents(result.getData().orElseThrow());
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handleCreateEvent(EventManagementView view, String companyId,
                                  String title, String description,
                                  LocalDateTime date, String category, String artist, String location) {
        String token = getToken();
        if (token == null) { UI.getCurrent().navigate("login"); return; }
        Result<String> result = eventService.createEvent(token, companyId, title, description, date, category, artist, location);
        if (result.isSuccess()) {
            view.showSuccess("Event created successfully.");
            loadEvents(view, companyId);
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handlePublishEvent(EventManagementView view, String companyId, String eventId) {
        String token = getToken();
        if (token == null) { UI.getCurrent().navigate("login"); return; }
        Result<Void> result = eventService.publishEvent(token, eventId);
        if (!result.isSuccess()) { view.showError(result.getErrorMessage()); return; }

        // If the event is RAFFLE mode, ensure the raffle exists
        Result<com.sadna.group13a.application.DTO.EventDTO> eventResult =
                eventService.getEvent(token, eventId);
        if (eventResult.isSuccess() &&
                eventResult.getOrThrow().saleMode() == com.sadna.group13a.domain.Aggregates.Event.EventSaleMode.RAFFLE) {
            Result<com.sadna.group13a.application.DTO.RaffleDTO> existing =
                    raffleService.getRaffleByEventId(token, eventId);
            if (!existing.isSuccess()) {
                raffleService.createRaffle(token, eventId, companyId);
            }
        }

        view.showSuccess("Event published.");
        loadEvents(view, companyId);
    }

    public void handleUnpublishEvent(EventManagementView view, String companyId, String eventId) {
        String token = getToken();
        if (token == null) { UI.getCurrent().navigate("login"); return; }
        Result<Void> result = eventService.unpublishEvent(token, eventId);
        if (result.isSuccess()) {
            view.showSuccess("Event unpublished.");
            loadEvents(view, companyId);
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handleUpdateEvent(EventManagementView view, String companyId, String eventId,
                                   String title, String description,
                                   LocalDateTime date, String category, String artist) {
        String token = getToken();
        if (token == null) { UI.getCurrent().navigate("login"); return; }
        Result<Void> result = eventService.updateEventDetails(token, eventId, title, description, date, category, artist);
        if (result.isSuccess()) {
            view.showSuccess("Event updated.");
            loadEvents(view, companyId);
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handleConfigureVenue(EventManagementView view, String companyId, String eventId,
                                     String venueName, List<ZoneCreationDTO> zones) {
        String token = getToken();
        if (token == null) { UI.getCurrent().navigate("login"); return; }
        Result<Void> result = eventService.createVenueMap(token, eventId, venueName, zones);
        if (result.isSuccess()) {
            view.showSuccess("Venue map saved.");
            loadEvents(view, companyId);
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    /** Returns current venue map for an event so the edit dialog can prefill it. */
    public VenueMapDTO loadVenueMap(String eventId) {
        String token = getToken();
        if (token == null) return null;
        Result<VenueMapDTO> result = eventService.getVenueMap(token, eventId);
        return result.isSuccess() ? result.getOrThrow() : null;
    }

    public void handleSetSaleMode(EventManagementView view, String companyId,
                                  String eventId, EventSaleMode mode, Integer queueCapacity) {
        String token = getToken();
        if (token == null) { UI.getCurrent().navigate("login"); return; }

        if (mode == EventSaleMode.QUEUE) {
            int capacity = (queueCapacity != null && queueCapacity > 0)
                    ? queueCapacity
                    : initProperties.getMaxConcurrentUsersPerEvent();
            Result<Void> result = queueService.createQueue(token, eventId, capacity);
            if (!result.isSuccess()) {
                String err = result.getErrorMessage();
                if (err != null && err.contains("already exists")) {
                    view.showSuccess("Event is already in QUEUE mode (existing queue kept).");
                } else {
                    view.showError(err);
                    return;
                }
            } else {
                view.showSuccess("Sale mode set to QUEUE. Queue created with " + capacity + " concurrent slots.");
            }

        } else if (mode == EventSaleMode.RAFFLE) {
            Result<Void> modeResult = eventService.setSaleMode(token, eventId, mode);
            if (!modeResult.isSuccess()) { view.showError(modeResult.getErrorMessage()); return; }

            Result<String> raffleResult = raffleService.createRaffle(token, eventId, companyId);
            if (!raffleResult.isSuccess()) {
                String err = raffleResult.getErrorMessage();
                // Raffle may already exist if mode was already RAFFLE
                if (err != null && err.toLowerCase().contains("already")) {
                    view.showSuccess("Event is already in RAFFLE mode (existing raffle kept).");
                } else {
                    view.showError(err);
                    return;
                }
            } else {
                view.showSuccess("Sale mode set to RAFFLE. Raffle created automatically.");
            }

        } else {
            // Close any existing raffle before switching away from RAFFLE mode
            Result<com.sadna.group13a.application.DTO.RaffleDTO> existingRaffle =
                    raffleService.getRaffleByEventId(token, eventId);
            if (existingRaffle.isSuccess()) {
                raffleService.closeRaffle(token, existingRaffle.getOrThrow().id());
            }

            Result<Void> result = eventService.setSaleMode(token, eventId, mode);
            if (!result.isSuccess()) { view.showError(result.getErrorMessage()); return; }
            view.showSuccess("Sale mode set to " + mode.name() + ".");
        }
        loadEvents(view, companyId);
    }

    // ── Purchase policy ───────────────────────────────────────────

    public void handleSetPurchasePolicyAllowAll(EventManagementView view, String companyId, String eventId) {
        String token = getToken();
        if (token == null) { UI.getCurrent().navigate("login"); return; }
        Result<Void> result = eventService.setPurchasePolicy(token, eventId, new AllowAllPolicy());
        if (result.isSuccess()) {
            view.showSuccess("Purchase policy set to Allow All.");
            loadEvents(view, companyId);
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handleSetPurchasePolicyMaxTickets(EventManagementView view, String companyId,
                                                   String eventId, int max) {
        String token = getToken();
        if (token == null) { UI.getCurrent().navigate("login"); return; }
        try {
            Result<Void> result = eventService.setPurchasePolicy(token, eventId, new MaxTicketsPolicy(max));
            if (result.isSuccess()) {
                view.showSuccess("Purchase policy: max " + max + " ticket(s).");
                loadEvents(view, companyId);
            } else {
                view.showError(result.getErrorMessage());
            }
        } catch (Exception e) {
            view.showError(e.getMessage());
        }
    }

    public void handleSetPurchasePolicyMinTickets(EventManagementView view, String companyId,
                                                   String eventId, int min) {
        String token = getToken();
        if (token == null) { UI.getCurrent().navigate("login"); return; }
        try {
            Result<Void> result = eventService.setPurchasePolicy(token, eventId, new MinTicketsPolicy(min));
            if (result.isSuccess()) {
                view.showSuccess("Purchase policy: minimum " + min + " ticket(s) per order.");
                loadEvents(view, companyId);
            } else {
                view.showError(result.getErrorMessage());
            }
        } catch (Exception e) {
            view.showError(e.getMessage());
        }
    }

    public void handleSetPurchasePolicyAgeRestriction(EventManagementView view, String companyId,
                                                       String eventId, int minAge) {
        String token = getToken();
        if (token == null) { UI.getCurrent().navigate("login"); return; }
        try {
            Result<Void> result = eventService.setPurchasePolicy(token, eventId, new AgeRestrictionPolicy(minAge));
            if (result.isSuccess()) {
                view.showSuccess("Purchase policy: minimum age " + minAge + ".");
                loadEvents(view, companyId);
            } else {
                view.showError(result.getErrorMessage());
            }
        } catch (Exception e) {
            view.showError(e.getMessage());
        }
    }

    // ── Discount policy ───────────────────────────────────────────

    public void handleSetDiscountPolicyNone(EventManagementView view, String companyId, String eventId) {
        String token = getToken();
        if (token == null) { UI.getCurrent().navigate("login"); return; }
        Result<Void> result = eventService.setDiscountPolicy(token, eventId, new NoDiscountPolicy());
        if (result.isSuccess()) {
            view.showSuccess("Discount policy cleared (no discount).");
            loadEvents(view, companyId);
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handleSetDiscountPolicySimple(EventManagementView view, String companyId,
                                               String eventId, double pct,
                                               LocalDate start, LocalDate end) {
        String token = getToken();
        if (token == null) { UI.getCurrent().navigate("login"); return; }
        try {
            Result<Void> result = eventService.setDiscountPolicy(token, eventId,
                    new SimpleDiscount(pct / 100.0, start, end));
            if (result.isSuccess()) {
                view.showSuccess(String.format("Discount: %.0f%% from %s to %s.", pct, start, end));
                loadEvents(view, companyId);
            } else {
                view.showError(result.getErrorMessage());
            }
        } catch (Exception e) {
            view.showError(e.getMessage());
        }
    }

    public void handleBack(String companyId) {
        UI.getCurrent().navigate("company/" + companyId);
    }
}
