package com.sadna.group13a.presentation.views.company;

import com.sadna.group13a.application.DTO.EventDTO;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.EventService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class EventManagementPresenter {

    private final EventService eventService;

    public EventManagementPresenter(EventService eventService) {
        this.eventService = eventService;
    }

    private String getToken() {
        return (String) VaadinSession.getCurrent().getAttribute("token");
    }

    public void loadEvents(EventManagementView view, String companyId) {
        String token = getToken();
        if (token == null) {
            UI.getCurrent().navigate("login");
            return;
        }
        Result<List<EventDTO>> result = eventService.getCompanyEvents(token, companyId);
        if (result.isSuccess()) {
            view.displayEvents(result.getData().orElseThrow());
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handleCreateEvent(EventManagementView view, String companyId,
                                  String title, String description,
                                  LocalDateTime date, String category, String location) {
        String token = getToken();
        if (token == null) {
            UI.getCurrent().navigate("login");
            return;
        }
        Result<String> result = eventService.createEvent(token, companyId, title, description, date, category, location);
        if (result.isSuccess()) {
            view.showSuccess("Event created successfully.");
            loadEvents(view, companyId);
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handlePublishEvent(EventManagementView view, String companyId, String eventId) {
        String token = getToken();
        if (token == null) {
            UI.getCurrent().navigate("login");
            return;
        }
        Result<Void> result = eventService.publishEvent(token, eventId);
        if (result.isSuccess()) {
            view.showSuccess("Event published.");
            loadEvents(view, companyId);
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handleUnpublishEvent(EventManagementView view, String companyId, String eventId) {
        String token = getToken();
        if (token == null) {
            UI.getCurrent().navigate("login");
            return;
        }
        Result<Void> result = eventService.unpublishEvent(token, eventId);
        if (result.isSuccess()) {
            view.showSuccess("Event unpublished.");
            loadEvents(view, companyId);
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handleBack(String companyId) {
        UI.getCurrent().navigate("company/" + companyId);
    }
}
