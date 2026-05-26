package com.sadna.group13a.presentation.views.admin;

import com.sadna.group13a.application.DTO.TicketQueueDTO;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.AdminService;
import com.sadna.group13a.domain.Aggregates.TicketQueue.TicketQueue;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AdminQueuePresenter {

    private final AdminService adminService;

    public AdminQueuePresenter(AdminService adminService) {
        this.adminService = adminService;
    }

    // Issue #3 fix: guard against null VaadinSession
    private String getToken() {
        VaadinSession session = VaadinSession.getCurrent();
        return session == null ? null : (String) session.getAttribute("token");
    }

    // Issue #4 fix: used by BeforeEnterObserver to gate access before rendering
    public boolean hasAdminAccess() {
        String token = getToken();
        if (token == null) return false;
        return adminService.viewAllQueues(token).isSuccess();
    }

    // Issue #5 fix: called from BeforeEnterObserver instead of addAttachListener
    public void loadQueues(AdminQueueView view) {
        String token = getToken();
        if (token == null) {
            UI.getCurrent().navigate("login");
            return;
        }
        // Issue #6 fix: map domain objects to DTOs before passing to view
        Result<List<TicketQueue>> result = adminService.viewAllQueues(token);
        if (result.isSuccess()) {
            List<TicketQueueDTO> dtos = result.getData().orElse(List.of()).stream()
                    .map(q -> new TicketQueueDTO(
                            q.getEventId(),
                            q.getMaxConcurrentUsers(),
                            q.getActiveCount(),
                            q.getWaitingCount()))
                    .toList();
            view.displayQueues(dtos);
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handleClearQueue(String eventId, AdminQueueView view) {
        // Issue #1 fix: trim before validation and use
        String id = eventId.trim();
        if (id.isBlank()) {
            view.showError("Please enter an Event ID.");
            return;
        }
        // Issue #2 fix: null-check token before action
        String token = getToken();
        if (token == null) {
            UI.getCurrent().navigate("login");
            return;
        }
        Result<Void> result = adminService.clearEventQueue(token, id);
        if (result.isSuccess()) {
            view.showSuccess("Queue for event '" + id + "' cleared.");
            loadQueues(view);
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handleAdjustCapacity(String eventId, String newMaxStr, AdminQueueView view) {
        // Issue #1 fix: trim before validation and use
        String id = eventId.trim();
        if (id.isBlank()) {
            view.showError("Please enter an Event ID.");
            return;
        }
        int newMax;
        try {
            newMax = Integer.parseInt(newMaxStr.trim());
        } catch (NumberFormatException e) {
            view.showError("Max users must be a valid number.");
            return;
        }
        // Issue #2 fix: null-check token before action
        String token = getToken();
        if (token == null) {
            UI.getCurrent().navigate("login");
            return;
        }
        Result<Void> result = adminService.adjustQueueRate(token, id, newMax);
        if (result.isSuccess()) {
            view.showSuccess("Capacity for event '" + id + "' set to " + newMax + ".");
            loadQueues(view);
        } else {
            view.showError(result.getErrorMessage());
        }
    }
}
