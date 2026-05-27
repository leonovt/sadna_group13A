package com.sadna.group13a.presentation.views.company;

import com.sadna.group13a.application.DTO.CompanyDTO;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.CompanyService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.stereotype.Component;

@Component
public class CompanyDashboardPresenter {

    private final CompanyService companyService;

    public CompanyDashboardPresenter(CompanyService companyService) {
        this.companyService = companyService;
    }

    private String getToken() {
        return (String) VaadinSession.getCurrent().getAttribute("token");
    }

    public void loadDashboard(CompanyDashboardView view, String companyId) {
        String token = getToken();
        if (token == null) { UI.getCurrent().navigate("login"); return; }
        Result<CompanyDTO> result = companyService.getCompany(token, companyId);
        if (result.isSuccess()) {
            view.displayCompany(result.getData().orElseThrow());
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handleSuspendCompany(String companyId, CompanyDashboardView view) {
        Result<Void> result = companyService.suspendCompany(getToken(), companyId);
        if (result.isSuccess()) {
            view.showSuccess("Company suspended.");
            loadDashboard(view, companyId);
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handleReopenCompany(String companyId, CompanyDashboardView view) {
        Result<Void> result = companyService.reopenCompany(getToken(), companyId);
        if (result.isSuccess()) {
            view.showSuccess("Company reopened.");
            loadDashboard(view, companyId);
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handleLogout() {
        VaadinSession.getCurrent().setAttribute("token", null);
        UI.getCurrent().navigate("login");
    }
}
