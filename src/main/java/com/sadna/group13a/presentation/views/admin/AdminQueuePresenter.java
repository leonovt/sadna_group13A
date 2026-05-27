package com.sadna.group13a.presentation.views.admin;

import com.sadna.group13a.application.DTO.TicketQueueDTO;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.AdminService;
import com.sadna.group13a.application.Services.QueueService;
import com.sadna.group13a.domain.Aggregates.TicketQueue.QueueTicket;
import com.sadna.group13a.domain.Aggregates.TicketQueue.TicketQueue;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AdminQueuePresenter {

    private final AdminService adminService;
    private final QueueService queueService;

    public AdminQueuePresenter(AdminService adminService, QueueService queueService) {
        this.adminService = adminService;
        this.queueService = queueService;
    }

    private String getToken() {
        VaadinSession session = VaadinSession.getCurrent();
        return session == null ? null : (String) session.getAttribute("token");
    }

    public boolean hasAdminAccess() {
        String token = getToken();
        if (token == null) return false;
        return adminService.viewAllQueues(token).isSuccess();
    }

    public void loadQueues(AdminQueueView view) {
        String token = getToken();
        if (token == null) { UI.getCurrent().navigate("login"); return; }
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
        String id = eventId.trim();
        if (id.isBlank()) { view.showError("Please enter an Event ID."); return; }
        String token = getToken();
        if (token == null) { UI.getCurrent().navigate("login"); return; }
        Result<Void> result = adminService.clearEventQueue(token, id);
        if (result.isSuccess()) {
            view.showSuccess("Queue for event '" + id + "' cleared.");
            loadQueues(view);
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handleAdjustCapacity(String eventId, String newMaxStr, AdminQueueView view) {
        String id = eventId.trim();
        if (id.isBlank()) { view.showError("Please enter an Event ID."); return; }
        int newMax;
        try {
            newMax = Integer.parseInt(newMaxStr.trim());
        } catch (NumberFormatException e) {
            view.showError("Max users must be a valid number.");
            return;
        }
        String token = getToken();
        if (token == null) { UI.getCurrent().navigate("login"); return; }
        Result<Void> result = adminService.adjustQueueRate(token, id, newMax);
        if (result.isSuccess()) {
            view.showSuccess("Capacity for event '" + id + "' set to " + newMax + ".");
            loadQueues(view);
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handleProcessBatch(String eventId, String batchSizeStr, AdminQueueView view) {
        String id = eventId.trim();
        if (id.isBlank()) { view.showError("Please enter an Event ID."); return; }
        int batchSize;
        try {
            batchSize = Integer.parseInt(batchSizeStr.trim());
        } catch (NumberFormatException e) {
            view.showError("Batch size must be a valid number.");
            return;
        }
        String token = getToken();
        if (token == null) { UI.getCurrent().navigate("login"); return; }
        Result<List<QueueTicket>> result = queueService.processBatch(token, id, batchSize);
        if (result.isSuccess()) {
            int admitted = result.getData().orElse(List.of()).size();
            view.showSuccess("Batch processed: " + admitted + " user(s) admitted for event '" + id + "'.");
            loadQueues(view);
        } else {
            view.showError(result.getErrorMessage());
        }
    }
}
