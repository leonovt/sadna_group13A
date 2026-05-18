package com.sadna.group13a.presentation.views.auth;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route("register")
@PageTitle("Register")
public class RegisterView extends VerticalLayout {

    private final RegisterPresenter presenter;

    public RegisterView(RegisterPresenter presenter) {
        this.presenter = presenter;
        initView();
    }

    private void initView() {
    }
}
