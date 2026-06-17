package com.sadna.group13a.presentation.views.member;

import com.sadna.group13a.application.DTO.InquiryDTO;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.InquiryService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class InquiryPresenter {

    private final InquiryService inquiryService;

    public InquiryPresenter(InquiryService inquiryService) {
        this.inquiryService = inquiryService;
    }

    public void loadMyInquiries(InquiryView view) {
        String token = currentToken();
        if (token == null) {
            UI.getCurrent().navigate("login");
            return;
        }
        Result<List<InquiryDTO>> result = inquiryService.getMyInquiries(token);
        if (result.isSuccess()) {
            view.displayInquiries(result.getOrThrow());
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handleSend(String companyId, String message, InquiryView view) {
        String token = currentToken();
        if (token == null) {
            UI.getCurrent().navigate("login");
            return;
        }
        Result<String> result = inquiryService.submitInquiry(token, companyId, message);
        if (result.isSuccess()) {
            view.showSuccess("Your inquiry has been sent to the company.");
            view.clearForm();
            loadMyInquiries(view);
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
