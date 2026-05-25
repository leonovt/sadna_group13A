package com.sadna.group13a.presentation.views.company;

import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.DTO.StaffMemberDTO;
import com.sadna.group13a.application.Services.CompanyService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StaffManagementPresenter {

    private final CompanyService companyService;

    public StaffManagementPresenter(CompanyService companyService) {
        this.companyService = companyService;
    }

    private String getToken() {
        return (String) VaadinSession.getCurrent().getAttribute("token");
    }

    public void loadStaff(StaffManagementView view, String companyId) {
        String token = getToken();
        if (token == null) {
            UI.getCurrent().navigate("login");
            return;
        }
        Result<List<StaffMemberDTO>> result = companyService.getRoleTree(token, companyId);
        if (result.isSuccess()) {
            view.displayStaff(result.getData().orElseThrow());
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handleBack(String companyId) {
        UI.getCurrent().navigate("company/" + companyId);
    }

    public void handleAppointManager(String username, String companyId, StaffManagementView view) {
        if (username == null || username.isBlank()) {
            view.showError("Username cannot be blank.");
            return;
        }
        String token = getToken();
        Result<Void> result = companyService.appointManager(token, companyId, username, null);
        if (result.isSuccess()) {
            view.showSuccess("Manager nomination sent to " + username + ".");
            loadStaff(view, companyId);
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handleFireStaff(String username, String companyId, StaffManagementView view) {
        if (username == null || username.isBlank()) {
            view.showError("Username cannot be blank.");
            return;
        }
        String token = getToken();
        Result<Void> result = companyService.fireManager(token, companyId, username);
        if (result.isSuccess()) {
            view.showSuccess(username + " has been removed from staff.");
            loadStaff(view, companyId);
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handleResign(String companyId, StaffManagementView view) {
        String token = getToken();
        Result<Void> result = companyService.resign(token, companyId);
        if (result.isSuccess()) {
            UI.getCurrent().navigate("company/" + companyId);
        } else {
            view.showError(result.getErrorMessage());
        }
    }
}
