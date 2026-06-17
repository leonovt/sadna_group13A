package com.sadna.group13a.presentation.views.member;

import com.sadna.group13a.application.DTO.ComplaintDTO;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.ComplaintService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ComplaintPresenter {

    private final ComplaintService complaintService;

    public ComplaintPresenter(ComplaintService complaintService) {
        this.complaintService = complaintService;
    }

    public void loadMyComplaints(ComplaintView view) {
        String token = currentToken();
        if (token == null) {
            UI.getCurrent().navigate("login");
            return;
        }
        Result<List<ComplaintDTO>> result = complaintService.getMyComplaints(token);
        if (result.isSuccess()) {
            view.displayComplaints(result.getOrThrow());
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handleSubmit(String subject, String message, ComplaintView view) {
        String token = currentToken();
        if (token == null) {
            UI.getCurrent().navigate("login");
            return;
        }
        Result<String> result = complaintService.submitComplaint(token, subject, message);
        if (result.isSuccess()) {
            view.showSuccess("Your complaint has been submitted to the administrators.");
            view.clearForm();
            loadMyComplaints(view);
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    private String currentToken() {
        VaadinSession session = VaadinSession.getCurrent();
        if (session == null) {
            return null;
        }
        Object token = session.getAttribute("token");
        return token == null ? null : (String) token;
    }
}
