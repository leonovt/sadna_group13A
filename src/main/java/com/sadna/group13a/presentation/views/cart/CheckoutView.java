package com.sadna.group13a.presentation.views.cart;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route("checkout")
@PageTitle("Checkout")
public class CheckoutView extends VerticalLayout {

    private final CheckoutPresenter presenter;

    public CheckoutView(CheckoutPresenter presenter) {
        this.presenter = presenter;
        initView();
    }

    private void initView() {
    }
}
