package com.sadna.group13a.presentation.views.member;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route("member")
@PageTitle("Dashboard")
public class MemberDashboardView extends VerticalLayout {

    private final MemberDashboardPresenter presenter;

    public MemberDashboardView(MemberDashboardPresenter presenter) {
        this.presenter = presenter;
        initView();
    }

    private void initView() {
        add(new Button("<- Home", e -> UI.getCurrent().navigate("")));
    }
}
