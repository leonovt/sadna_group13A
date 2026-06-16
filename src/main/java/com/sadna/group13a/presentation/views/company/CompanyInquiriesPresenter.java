package com.sadna.group13a.presentation.views.company;

import com.sadna.group13a.application.DTO.InquiryDTO;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.InquiryService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CompanyInquiriesPresenter {

    private final InquiryService inquiryService;

    public CompanyInquiriesPresenter(InquiryService inquiryService) {
        this.inquiryService = inquiryService;
    }

    public void loadInquiries(CompanyInquiriesView view, String companyId) {
        String token = currentToken();
        if (token == null) {
            UI.getCurrent().navigate("login");
            return;
        }
        Result<List<InquiryDTO>> result = inquiryService.getCompanyInquiries(token, companyId);
        if (result.isSuccess()) {
            view.displayInquiries(result.getOrThrow());
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handleRespond(String inquiryId, String response, String companyId, CompanyInquiriesView view) {
        String token = currentToken();
        if (token == null) {
            UI.getCurrent().navigate("login");
            return;
        }
        Result<Void> result = inquiryService.respondToInquiry(token, inquiryId, response);
        if (result.isSuccess()) {
            view.showSuccess("Reply sent to the buyer.");
            loadInquiries(view, companyId);
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handleBack(String companyId) {
        UI.getCurrent().navigate("company/" + companyId);
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
