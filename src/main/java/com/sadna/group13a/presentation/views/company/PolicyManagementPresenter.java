package com.sadna.group13a.presentation.views.company;

import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.CompanyService;
import com.sadna.group13a.application.DTO.StaffMemberDTO;
import com.sadna.group13a.domain.Aggregates.Company.CompanyPermission;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class PolicyManagementPresenter {

    private final CompanyService companyService;

    public PolicyManagementPresenter(CompanyService companyService) {
        this.companyService = companyService;
    }

    private String getToken() {
        return (String) VaadinSession.getCurrent().getAttribute("token");
    }

    public void loadStaff(PolicyManagementView view, String companyId) {
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

    public void handleUpdatePermissions(String targetUsername, Set<CompanyPermission> permissions,
                                        String companyId, PolicyManagementView view) {
        if (targetUsername == null || targetUsername.isBlank()) {
            view.showError("Manager username must not be blank.");
            return;
        }
        String token = getToken();
        if (token == null) {
            UI.getCurrent().navigate("login");
            return;
        }
        Result<Void> result = companyService.updatePermissions(token, companyId, targetUsername, permissions);
        if (result.isSuccess()) {
            view.showSuccess("Permissions updated successfully.");
            loadStaff(view, companyId);
        } else {
            view.showError(result.getErrorMessage());
        }
    }
}
