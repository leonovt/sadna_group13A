package com.sadna.group13a.presentation.views.member;

import com.sadna.group13a.application.Services.UserService;
import org.springframework.stereotype.Component;

@Component
public class ProfilePresenter {

    private final UserService userService;

    public ProfilePresenter(UserService userService) {
        this.userService = userService;
    }
}
