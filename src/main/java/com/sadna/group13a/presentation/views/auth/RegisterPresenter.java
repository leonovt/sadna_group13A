package com.sadna.group13a.presentation.views.auth;

import com.sadna.group13a.application.Services.UserService;
import org.springframework.stereotype.Component;

@Component
public class RegisterPresenter {

    private final UserService userService;

    public RegisterPresenter(UserService userService) {
        this.userService = userService;
    }
}
