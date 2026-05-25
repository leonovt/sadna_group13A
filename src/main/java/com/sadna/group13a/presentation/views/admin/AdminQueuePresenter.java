package com.sadna.group13a.presentation.views.admin;

import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.AdminService;
import com.sadna.group13a.application.Services.QueueService;
import com.sadna.group13a.domain.Aggregates.TicketQueue.TicketQueue;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AdminQueuePresenter {

    private final QueueService queueService;
    private final AdminService adminService;

    public AdminQueuePresenter(QueueService queueService, AdminService adminService) {
        this.queueService = queueService;
        this.adminService = adminService;
    }

    private String getToken() {
        return (String) VaadinSession.getCurrent().getAttribute("token");
    }

    public void loadQueues(AdminQueueView view) {
        String token = getToken();
        if (token == null) {
            UI.getCurrent().navigate("login");
            return;
        }
        Result<List<TicketQueue>> result = adminService.viewAllQueues(token);
        if (result.isSuccess()) {
            view.displayQueues(result.getData().orElse(List.of()));
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handleClearQueue(String eventId, AdminQueueView view) {
        if (eventId.isBlank()) {
            view.showError("Please enter an Event ID.");
            return;
        }
        Result<Void> result = adminService.clearEventQueue(getToken(), eventId);
        if (result.isSuccess()) {
            view.showSuccess("Queue for event '" + eventId + "' cleared.");
            loadQueues(view);
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handleAdjustCapacity(String eventId, String newMaxStr, AdminQueueView view) {
        if (eventId.isBlank()) {
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
        Result<Void> result = adminService.adjustQueueRate(getToken(), eventId, newMax);
        if (result.isSuccess()) {
            view.showSuccess("Capacity for event '" + eventId + "' set to " + newMax + ".");
            loadQueues(view);
        } else {
            view.showError(result.getErrorMessage());
        }
    }
}
