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

    // Issue #2 fix: guard against null VaadinSession
    private String getToken() {
        VaadinSession session = VaadinSession.getCurrent();
        return session == null ? null : (String) session.getAttribute("token");
    }

    // Issue #1 & #3 fix: used by BeforeEnterObserver to gate access before rendering
    public boolean hasAdminAccess() {
        String token = getToken();
        if (token == null) return false;
        return adminService.viewGlobalPurchaseHistory(token).isSuccess();
    }

    // Issue #3 fix: called from BeforeEnterObserver instead of addAttachListener
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
