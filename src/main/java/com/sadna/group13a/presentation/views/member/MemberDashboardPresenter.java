package com.sadna.group13a.presentation.views.member;

import com.sadna.group13a.application.Services.CompanyService;
import com.sadna.group13a.application.Services.UserService;
import org.springframework.stereotype.Component;

@Component
public class MemberDashboardPresenter {

    private final UserService userService;
    private final CompanyService companyService;

    public MemberDashboardPresenter(UserService userService, CompanyService companyService) {
        this.userService = userService;
        this.companyService = companyService;
    }
}
