package com.sadna.group13a.presentation.views.member;

import com.sadna.group13a.application.DTO.UserDTO;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.UserService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.stereotype.Component;

@Component
public class ProfilePresenter {

    private final UserService userService;

    public ProfilePresenter(UserService userService) {
        this.userService = userService;
    }

    /** Loads the signed-in user's profile into the form. */
    public void loadProfile(ProfileView view) {
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

    /** Renames the current user; the refreshed profile is reflected back into the form. */
    public void handleUpdateUsername(String newUsername, ProfileView view) {
        String token = currentToken();
        if (token == null) {
            UI.getCurrent().navigate("login");
            return;
        }
        if (newUsername == null || newUsername.isBlank()) {
            view.showError("Username cannot be blank.");
            return;
        }

        Result<UserDTO> result = userService.updateProfile(token, newUsername.trim());
        if (result.isSuccess()) {
            view.showProfile(result.getOrThrow());
            view.showInfo("Profile updated.");
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    private String currentToken() {
        // VaadinSession.getCurrent() can be null when called outside a Vaadin
        // request thread, so it must be guarded before dereferencing.
        VaadinSession session = VaadinSession.getCurrent();
        if (session == null) {
            return null;
        }
        Object token = session.getAttribute("token");
        return token == null ? null : (String) token;
    }
}
