package com.sadna.group13a.presentation.views.admin;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route("admin/users")
@PageTitle("User Management")
public class AdminUserManagementView extends VerticalLayout {

    private final AdminUserManagementPresenter presenter;

    public AdminUserManagementView(AdminUserManagementPresenter presenter) {
        this.presenter = presenter;
        initView();
    }

    private void initView() {
    }
}
