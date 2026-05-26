package com.sadna.group13a.presentation.views.member;

import com.sadna.group13a.application.DTO.UserDTO;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.CompanyService;
import com.sadna.group13a.application.Services.UserService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.stereotype.Component;

@Component
public class MemberDashboardPresenter {

    private final UserService userService;
    private final CompanyService companyService;

    public MemberDashboardPresenter(UserService userService, CompanyService companyService) {
        this.userService = userService;
        this.companyService = companyService;
    }

    /** Loads the signed-in member's profile to populate the greeting. */
    public void loadDashboard(MemberDashboardView view) {
        String token = currentToken();
        if (token == null) {
            UI.getCurrent().navigate("login");
            return;
        }

        Result<UserDTO> result = userService.getUserProfile(token);
        if (result.isSuccess()) {
            view.showProfile(result.getOrThrow());
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    /** Creates a production company with the current member as founder. */
    public void handleCreateCompany(String name, String description, MemberDashboardView view) {
        String token = currentToken();
        if (token == null) {
            UI.getCurrent().navigate("login");
            return;
        }
        if (name == null || name.isBlank()) {
            view.showError("Company name is required.");
            return;
        }

        String trimmedName = name.trim();
        Result<Boolean> result = companyService.createCompany(
                token, trimmedName, description == null ? "" : description.trim());
        if (result.isSuccess()) {
            view.clearCompanyForm();
            view.showInfo("Company '" + trimmedName + "' created.");
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    /** Logs out; the service returns a fresh guest token that replaces the session token. */
    public void handleLogout(MemberDashboardView view) {
        String token = currentToken();
        if (token == null) {
            UI.getCurrent().navigate("login");
            return;
        }

        Result<String> result = userService.logout(token);
        if (result.isSuccess()) {
            VaadinSession.getCurrent().setAttribute("token", result.getOrThrow());
            UI.getCurrent().navigate("login");
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    private String currentToken() {
        Object token = VaadinSession.getCurrent().getAttribute("token");
        return token == null ? null : (String) token;
    }
}
