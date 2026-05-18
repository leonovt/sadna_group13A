package com.sadna.group13a.presentation.views.company;

import com.sadna.group13a.application.Services.CompanyService;
import org.springframework.stereotype.Component;

@Component
public class PolicyManagementPresenter {

    private final CompanyService companyService;

    public PolicyManagementPresenter(CompanyService companyService) {
        this.companyService = companyService;
    }
}
