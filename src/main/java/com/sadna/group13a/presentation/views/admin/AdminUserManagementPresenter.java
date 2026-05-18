package com.sadna.group13a.presentation.views.admin;

import com.sadna.group13a.application.Services.AdminService;
import org.springframework.stereotype.Component;

@Component
public class AdminUserManagementPresenter {

    private final AdminService adminService;

    public AdminUserManagementPresenter(AdminService adminService) {
        this.adminService = adminService;
    }
}
