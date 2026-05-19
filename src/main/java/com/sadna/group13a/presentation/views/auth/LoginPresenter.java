package com.sadna.group13a.presentation.views.auth;

import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.UserService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.stereotype.Component;

@Component
public class LoginPresenter {

    private final UserService userService;

    public LoginPresenter(UserService userService) {
        this.userService = userService;
    }

    public void handleLogin(String username, String password, LoginView view) {
        Result<String> result = userService.login(username, password);
        if (result.isSuccess()) {
            VaadinSession.getCurrent().setAttribute("token", result.getOrThrow());
            UI.getCurrent().navigate("");
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handleGuestLogin(LoginView view) {
        Result<String> result = userService.enterAsGuest();
        if (result.isSuccess()) {
            VaadinSession.getCurrent().setAttribute("token", result.getOrThrow());
            UI.getCurrent().navigate("");
        } else {
            view.showError("Failed to start guest session. Please try again.");
        }
    }
}
