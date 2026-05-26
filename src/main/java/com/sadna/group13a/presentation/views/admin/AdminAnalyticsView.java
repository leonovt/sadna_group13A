package com.sadna.group13a.presentation.views.admin;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route("admin/analytics")
@PageTitle("Analytics")
public class AdminAnalyticsView extends VerticalLayout {

    private final AdminAnalyticsPresenter presenter;

    public AdminAnalyticsView(AdminAnalyticsPresenter presenter) {
        this.presenter = presenter;
        initView();
    }

    private void initView() {
        add(new Button("<- Home", e -> UI.getCurrent().navigate("")));
    }
}
