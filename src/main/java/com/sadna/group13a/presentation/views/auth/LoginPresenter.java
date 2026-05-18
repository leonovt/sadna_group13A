package com.sadna.group13a.presentation.views.auth;

import com.sadna.group13a.application.Services.UserService;
import org.springframework.stereotype.Component;

@Component
public class LoginPresenter {

    private final UserService userService;

    public LoginPresenter(UserService userService) {
        this.userService = userService;
    }
}
