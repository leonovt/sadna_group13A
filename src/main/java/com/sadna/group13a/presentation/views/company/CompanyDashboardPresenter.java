package com.sadna.group13a.presentation.views.company;

import com.sadna.group13a.application.Services.CompanyService;
import org.springframework.stereotype.Component;

@Component
public class CompanyDashboardPresenter {

    private final CompanyService companyService;

    public CompanyDashboardPresenter(CompanyService companyService) {
        this.companyService = companyService;
    }
}
