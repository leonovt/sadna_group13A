package com.sadna.group13a.presentation.views.home;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route("")
@PageTitle("Events")
public class HomeView extends VerticalLayout {

    private final HomePresenter presenter;

    public HomeView(HomePresenter presenter) {
        this.presenter = presenter;
        initView();
    }

    private void initView() {
    }
}
