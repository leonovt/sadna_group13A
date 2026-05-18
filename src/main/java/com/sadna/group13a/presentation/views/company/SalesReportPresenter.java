package com.sadna.group13a.presentation.views.company;

import com.sadna.group13a.application.Services.CompanyService;
import org.springframework.stereotype.Component;

@Component
public class SalesReportPresenter {

    private final CompanyService companyService;

    public SalesReportPresenter(CompanyService companyService) {
        this.companyService = companyService;
    }
}
