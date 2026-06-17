package com.sadna.group13a.presentation.views.admin;

import com.sadna.group13a.application.DTO.ComplaintDTO;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.ComplaintService;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AdminComplaintsPresenter {

    private final ComplaintService complaintService;

    public AdminComplaintsPresenter(ComplaintService complaintService) {
        this.complaintService = complaintService;
    }

    public boolean hasAdminAccess() {
        String token = getToken();
        return token != null && complaintService.getAllComplaints(token).isSuccess();
    }

    public void loadComplaints(AdminComplaintsView view) {
        String token = getToken();
        if (token == null) {
            return;
        }
        Result<List<ComplaintDTO>> result = complaintService.getAllComplaints(token);
        if (result.isSuccess()) {
            view.displayComplaints(result.getOrThrow());
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handleRespond(String complaintId, String response, AdminComplaintsView view) {
        String token = getToken();
        if (token == null) {
            return;
        }
        Result<Void> result = complaintService.respondToComplaint(token, complaintId, response);
        if (result.isSuccess()) {
            view.showSuccess("Response sent and complaint resolved.");
            loadComplaints(view);
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    private String getToken() {
        VaadinSession session = VaadinSession.getCurrent();
        return session == null ? null : (String) session.getAttribute("token");
    }
}
