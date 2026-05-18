package com.sadna.group13a.presentation.views.admin;

import com.sadna.group13a.application.Services.AdminService;
import com.sadna.group13a.application.Services.SystemLogService;
import org.springframework.stereotype.Component;

@Component
public class AdminDashboardPresenter {

    private final AdminService adminService;
    private final SystemLogService systemLogService;

    public AdminDashboardPresenter(AdminService adminService, SystemLogService systemLogService) {
        this.adminService = adminService;
        this.systemLogService = systemLogService;
    }
}
