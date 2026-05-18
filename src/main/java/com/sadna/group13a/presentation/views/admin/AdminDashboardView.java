package com.sadna.group13a.presentation.views.admin;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route("admin")
@PageTitle("Admin Dashboard")
public class AdminDashboardView extends VerticalLayout {

    private final AdminDashboardPresenter presenter;

    public AdminDashboardView(AdminDashboardPresenter presenter) {
        this.presenter = presenter;
        initView();
    }

    private void initView() {
    }
}
