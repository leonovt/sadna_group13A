package com.sadna.group13a.presentation.views.member;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route("member/profile")
@PageTitle("Profile")
public class ProfileView extends VerticalLayout {

    private final ProfilePresenter presenter;

    public ProfileView(ProfilePresenter presenter) {
        this.presenter = presenter;
        initView();
    }

    private void initView() {
    }
}
