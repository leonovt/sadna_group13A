package com.sadna.group13a.presentation.views.company;

import com.sadna.group13a.application.DTO.SalesReportDTO;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.CompanyService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.stereotype.Component;

@Component
public class SalesReportPresenter {

    private final CompanyService companyService;

    public SalesReportPresenter(CompanyService companyService) {
        this.companyService = companyService;
    }

    private String getToken() {
        return (String) VaadinSession.getCurrent().getAttribute("token");
    }

    public void loadReport(SalesReportView view, String companyId) {
        String token = getToken();
        if (token == null) {
            UI.getCurrent().navigate("login");
            return;
        }
        Result<SalesReportDTO> result = companyService.generateSalesReport(token, companyId);
        if (result.isSuccess()) {
            view.displayReport(result.getData().orElseThrow());
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handleBack(String companyId) {
        UI.getCurrent().navigate("company/" + companyId);
    }
}
