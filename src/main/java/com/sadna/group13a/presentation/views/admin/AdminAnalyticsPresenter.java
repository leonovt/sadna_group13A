package com.sadna.group13a.presentation.views.admin;

import com.sadna.group13a.application.DTO.OrderHistoryDTO;
import com.sadna.group13a.application.DTO.SystemAnalyticsDTO;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.AdminService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AdminAnalyticsPresenter {

    private final AdminService adminService;

    public AdminAnalyticsPresenter(AdminService adminService) {
        this.adminService = adminService;
    }

    // Issue #7 fix: guard against null VaadinSession
    private String getToken() {
        VaadinSession session = VaadinSession.getCurrent();
        return session == null ? null : (String) session.getAttribute("token");
    }

    // Issue #5 fix: used by BeforeEnterObserver to gate access before rendering
    public boolean hasAdminAccess() {
        String token = getToken();
        if (token == null) return false;
        return adminService.getSystemAnalytics(token).isSuccess();
    }

    // Issue #6 fix: called from BeforeEnterObserver instead of addAttachListener
    public void loadAnalytics(AdminAnalyticsView view) {
        String token = getToken();
        if (token == null) {
            UI.getCurrent().navigate("login");
            return;
        }

        // Issue #4 fix: use orElse(null) instead of orElseThrow inside success branch
        Result<SystemAnalyticsDTO> analyticsResult = adminService.getSystemAnalytics(token);
        if (analyticsResult.isSuccess()) {
            SystemAnalyticsDTO analytics = analyticsResult.getData().orElse(null);
            if (analytics != null) {
                view.displayAnalytics(analytics);
            }
        } else {
            view.showError(analyticsResult.getErrorMessage());
        }

        // Issue #1 fix: add else branch so revenue section is not silently left blank
        Result<List<OrderHistoryDTO>> historyResult = adminService.viewGlobalPurchaseHistory(token);
        if (historyResult.isSuccess()) {
            List<OrderHistoryDTO> orders = historyResult.getData().orElse(List.of());
            double totalRevenue = orders.stream().mapToDouble(OrderHistoryDTO::totalPaid).sum();
            view.displayRevenue(totalRevenue, orders.size());
        } else {
            view.displayRevenue(0, 0);
            view.showError(historyResult.getErrorMessage());
        }

        // Issue #2 fix: add else branch so event log section is not silently left blank
        Result<List<String>> eventLogResult = adminService.getEventLog(token);
        if (eventLogResult.isSuccess()) {
            view.displayEventLog(eventLogResult.getData().orElse(List.of()));
        } else {
            view.displayEventLog(List.of());
        }

        // Issue #3 fix: add else branch so error log section is not silently left blank
        Result<List<String>> errorLogResult = adminService.getErrorLog(token);
        if (errorLogResult.isSuccess()) {
            view.displayErrorLog(errorLogResult.getData().orElse(List.of()));
        } else {
            view.displayErrorLog(List.of());
        }
    }
}
