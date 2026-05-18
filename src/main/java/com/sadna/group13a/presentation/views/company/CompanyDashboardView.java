package com.sadna.group13a.presentation.views.company;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route("company/:companyId")
@PageTitle("Company")
public class CompanyDashboardView extends VerticalLayout implements BeforeEnterObserver {

    private final CompanyDashboardPresenter presenter;
    private String companyId;

    public CompanyDashboardView(CompanyDashboardPresenter presenter) {
        this.presenter = presenter;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        companyId = event.getRouteParameters().get("companyId").orElse("");
        initView();
    }

    private void initView() {
    }
}
