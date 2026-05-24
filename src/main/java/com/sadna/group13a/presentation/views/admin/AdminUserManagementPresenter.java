package com.sadna.group13a.presentation.views.admin;

import com.sadna.group13a.application.DTO.OrderHistoryDTO;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.AdminService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AdminUserManagementPresenter {

    private final AdminService adminService;

    public AdminUserManagementPresenter(AdminService adminService) {
        this.adminService = adminService;
    }

    private String getToken() {
        return (String) VaadinSession.getCurrent().getAttribute("token");
    }

    public void loadPurchaseHistory(AdminUserManagementView view) {
        String token = getToken();
        if (token == null) {
            UI.getCurrent().navigate("login");
            return;
        }
        Result<List<OrderHistoryDTO>> result = adminService.viewGlobalPurchaseHistory(token);
        if (result.isSuccess()) {
            view.displayPurchaseHistory(result.getData().orElse(List.of()));
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handleDeactivateUser(String username, AdminUserManagementView view) {
        if (username.isBlank()) {
            view.showError("Please enter a username.");
            return;
        }
        Result<Void> result = adminService.deactivateUser(getToken(), username);
        if (result.isSuccess()) {
            view.showSuccess("User '" + username + "' deactivated.");
            loadPurchaseHistory(view);
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handleReactivateUser(String username, AdminUserManagementView view) {
        if (username.isBlank()) {
            view.showError("Please enter a username.");
            return;
        }
        Result<Void> result = adminService.reactivateUser(getToken(), username);
        if (result.isSuccess()) {
            view.showSuccess("User '" + username + "' reactivated.");
            loadPurchaseHistory(view);
        } else {
            view.showError(result.getErrorMessage());
        }
    }
}
