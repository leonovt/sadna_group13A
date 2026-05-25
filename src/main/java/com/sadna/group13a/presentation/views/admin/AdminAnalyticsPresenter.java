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

    private String getToken() {
        return (String) VaadinSession.getCurrent().getAttribute("token");
    }

    public void loadAnalytics(AdminAnalyticsView view) {
        String token = getToken();
        if (token == null) {
            UI.getCurrent().navigate("login");
            return;
        }

        Result<SystemAnalyticsDTO> analyticsResult = adminService.getSystemAnalytics(token);
        if (analyticsResult.isSuccess()) {
            view.displayAnalytics(analyticsResult.getData().orElseThrow());
        } else {
            view.showError(analyticsResult.getErrorMessage());
        }

        Result<List<OrderHistoryDTO>> historyResult = adminService.viewGlobalPurchaseHistory(token);
        if (historyResult.isSuccess()) {
            List<OrderHistoryDTO> orders = historyResult.getData().orElse(List.of());
            double totalRevenue = orders.stream().mapToDouble(OrderHistoryDTO::totalPaid).sum();
            view.displayRevenue(totalRevenue, orders.size());
        }

        Result<List<String>> eventLogResult = adminService.getEventLog(token);
        if (eventLogResult.isSuccess()) {
            view.displayEventLog(eventLogResult.getData().orElse(List.of()));
        }

        Result<List<String>> errorLogResult = adminService.getErrorLog(token);
        if (errorLogResult.isSuccess()) {
            view.displayErrorLog(errorLogResult.getData().orElse(List.of()));
        }
    }
}
