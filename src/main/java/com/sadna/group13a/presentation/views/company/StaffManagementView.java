package com.sadna.group13a.presentation.views.company;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route("company/:companyId/staff")
@PageTitle("Staff Management")
public class StaffManagementView extends VerticalLayout implements BeforeEnterObserver {

    private final StaffManagementPresenter presenter;
    private String companyId;

    public StaffManagementView(StaffManagementPresenter presenter) {
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
