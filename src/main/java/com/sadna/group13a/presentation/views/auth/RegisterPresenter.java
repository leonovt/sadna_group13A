package com.sadna.group13a.presentation.views.auth;

import com.sadna.group13a.application.Services.UserService;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.DTO.UserDTO;
import com.vaadin.flow.component.UI;
import org.springframework.stereotype.Component;

@Component
public class RegisterPresenter {

    private final UserService userService;

    public RegisterPresenter(UserService userService) {
        this.userService = userService;
    }

    public void handleRegister(String username, String password, String confirmPassword, RegisterView view) {
        if (username.isBlank()) {
            view.showError("Username is required.");
            return;
        }
        if (password.isBlank()) {
            view.showError("Password is required.");
            return;
        }
        if (!password.equals(confirmPassword)) {
            view.showError("Passwords do not match.");
            return;
        }
        Result<UserDTO> result = userService.register(username, password);
        if (result.isSuccess()) {
            UI.getCurrent().navigate("login");
        } else {
            view.showError(result.getErrorMessage());
        }
    }
}
