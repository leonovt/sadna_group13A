package com.sadna.group13a.presentation.views.admin;

import com.sadna.group13a.application.DTO.SystemAnalyticsDTO;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.AdminService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AdminDashboardPresenter {

    private final AdminService adminService;

    public AdminDashboardPresenter(AdminService adminService) {
        this.adminService = adminService;
    }

    // Issue #2 fix: guard against null VaadinSession
    private String getToken() {
        VaadinSession session = VaadinSession.getCurrent();
        return session == null ? null : (String) session.getAttribute("token");
    }

    // Issue #1 & #4 fix: used by BeforeEnterObserver to gate access before rendering
    public boolean hasAdminAccess() {
        String token = getToken();
        if (token == null) return false;
        return adminService.getSystemAnalytics(token).isSuccess();
    }

    // Issue #4 fix: called from BeforeEnterObserver instead of addAttachListener
    public void loadDashboard(AdminDashboardView view) {
        String token = getToken();
        if (token == null) {
            UI.getCurrent().navigate("login");
            return;
        }

        Result<SystemAnalyticsDTO> analyticsResult = adminService.getSystemAnalytics(token);
        if (analyticsResult.isSuccess()) {
            // Issue #3 fix: orElse(null) with explicit null guard instead of orElseThrow
            SystemAnalyticsDTO analytics = analyticsResult.getData().orElse(null);
            if (analytics != null) {
                view.displayAnalytics(analytics);
            }
        } else {
            view.showError(analyticsResult.getErrorMessage());
        }

        Result<List<String>> logResult = adminService.getEventLog(token);
        if (logResult.isSuccess()) {
            view.displayEventLog(logResult.getData().orElse(List.of()));
        }
    }

    public void handleDeactivateUser(String username, AdminDashboardView view) {
        if (username.isBlank()) {
            view.showError("Please enter a username.");
            return;
        }
        Result<Void> result = adminService.deactivateUser(getToken(), username);
        if (result.isSuccess()) {
            view.showSuccess("User '" + username + "' deactivated.");
            loadDashboard(view);
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handleReactivateUser(String username, AdminDashboardView view) {
        if (username.isBlank()) {
            view.showError("Please enter a username.");
            return;
        }
        Result<Void> result = adminService.reactivateUser(getToken(), username);
        if (result.isSuccess()) {
            view.showSuccess("User '" + username + "' reactivated.");
            loadDashboard(view);
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handleCancelEvent(String eventId, AdminDashboardView view) {
        if (eventId.isBlank()) {
            view.showError("Please enter an Event ID.");
            return;
        }
        Result<Void> result = adminService.cancelEventGlobally(getToken(), eventId);
        if (result.isSuccess()) {
            view.showSuccess("Event '" + eventId + "' cancelled.");
            loadDashboard(view);
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handleCloseCompany(String companyId, AdminDashboardView view) {
        if (companyId.isBlank()) {
            view.showError("Please enter a Company ID.");
            return;
        }
        Result<Void> result = adminService.closeCompanyGlobally(getToken(), companyId);
        if (result.isSuccess()) {
            view.showSuccess("Company '" + companyId + "' closed.");
            loadDashboard(view);
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handleLogout() {
        VaadinSession session = VaadinSession.getCurrent();
        if (session != null) {
            session.setAttribute("token", null);
        }
        UI.getCurrent().navigate("login");
    }
}
