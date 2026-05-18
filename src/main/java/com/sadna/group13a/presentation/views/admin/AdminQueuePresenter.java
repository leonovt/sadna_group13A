package com.sadna.group13a.presentation.views.admin;

import com.sadna.group13a.application.Services.AdminService;
import com.sadna.group13a.application.Services.QueueService;
import org.springframework.stereotype.Component;

@Component
public class AdminQueuePresenter {

    private final QueueService queueService;
    private final AdminService adminService;

    public AdminQueuePresenter(QueueService queueService, AdminService adminService) {
        this.queueService = queueService;
        this.adminService = adminService;
    }
}
