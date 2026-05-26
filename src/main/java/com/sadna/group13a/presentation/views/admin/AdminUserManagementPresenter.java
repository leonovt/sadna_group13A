package com.sadna.group13a.presentation.views.admin;

import com.sadna.group13a.application.DTO.OrderHistoryDTO;
import com.sadna.group13a.application.DTO.SuspensionDTO;
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
        VaadinSession session = VaadinSession.getCurrent();
        return session == null ? null : (String) session.getAttribute("token");
    }

    public boolean hasAdminAccess() {
        String token = getToken();
        if (token == null) return false;
        return adminService.viewGlobalPurchaseHistory(token).isSuccess();
    }

    public void loadPurchaseHistory(AdminUserManagementView view) {
        String token = getToken();
        if (token == null) { UI.getCurrent().navigate("login"); return; }
        Result<List<OrderHistoryDTO>> result = adminService.viewGlobalPurchaseHistory(token);
        if (result.isSuccess()) {
            view.displayPurchaseHistory(result.getData().orElse(List.of()));
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void loadSuspensions(AdminUserManagementView view) {
        String token = getToken();
        if (token == null) { UI.getCurrent().navigate("login"); return; }
        Result<List<SuspensionDTO>> result = adminService.viewSuspensions(token);
        if (result.isSuccess()) {
            view.displaySuspensions(result.getData().orElse(List.of()));
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handleDeactivateUser(String username, AdminUserManagementView view) {
        if (username.isBlank()) { view.showError("Please enter a username."); return; }
        Result<Void> result = adminService.deactivateUser(getToken(), username);
        if (result.isSuccess()) {
            view.showSuccess("User '" + username + "' deactivated.");
            loadPurchaseHistory(view);
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handleReactivateUser(String username, AdminUserManagementView view) {
        if (username.isBlank()) { view.showError("Please enter a username."); return; }
        Result<Void> result = adminService.reactivateUser(getToken(), username);
        if (result.isSuccess()) {
            view.showSuccess("User '" + username + "' reactivated.");
            loadPurchaseHistory(view);
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handleSuspendUser(String username, String durationDaysStr, AdminUserManagementView view) {
        if (username.isBlank()) { view.showError("Please enter a username."); return; }
        Long durationDays = null;
        if (!durationDaysStr.isBlank()) {
            try {
                durationDays = Long.parseLong(durationDaysStr.trim());
            } catch (NumberFormatException e) {
                view.showError("Duration must be a whole number of days, or leave blank for permanent.");
                return;
            }
        }
        Result<Void> result = adminService.suspendUser(getToken(), username, durationDays);
        if (result.isSuccess()) {
            String label = durationDays != null ? "for " + durationDays + " day(s)" : "permanently";
            view.showSuccess("User '" + username + "' suspended " + label + ".");
            loadSuspensions(view);
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handleLiftSuspension(String username, AdminUserManagementView view) {
        if (username.isBlank()) { view.showError("Please enter a username."); return; }
        Result<Void> result = adminService.liftSuspension(getToken(), username);
        if (result.isSuccess()) {
            view.showSuccess("Suspension lifted for '" + username + "'.");
            loadSuspensions(view);
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handleSendMessage(String username, String message, AdminUserManagementView view) {
        if (username.isBlank()) { view.showError("Please enter a username."); return; }
        if (message.isBlank()) { view.showError("Please enter a message."); return; }
        Result<Void> result = adminService.sendMessageToUser(getToken(), username, message);
        if (result.isSuccess()) {
            view.showSuccess("Message sent to '" + username + "'.");
        } else {
            view.showError(result.getErrorMessage());
        }
    }
}
