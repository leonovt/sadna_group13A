package com.sadna.group13a.presentation.views.member;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route("member/raffles")
@PageTitle("Raffles")
public class RaffleView extends VerticalLayout {

    private final RafflePresenter presenter;

    public RaffleView(RafflePresenter presenter) {
        this.presenter = presenter;
        initView();
    }

    private void initView() {
        add(new Button("<- Home", e -> UI.getCurrent().navigate("")));
    }
}
