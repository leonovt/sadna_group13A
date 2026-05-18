package com.sadna.group13a.presentation.views.auth;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route("login")
@PageTitle("Login")
public class LoginView extends VerticalLayout {

    private final LoginPresenter presenter;

    public LoginView(LoginPresenter presenter) {
        this.presenter = presenter;
        initView();
    }

    private void initView() {
    }
}
