package com.sadna.group13a.presentation.views.cart;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route("cart")
@PageTitle("Cart")
public class CartView extends VerticalLayout {

    private final CartPresenter presenter;

    public CartView(CartPresenter presenter) {
        this.presenter = presenter;
        initView();
    }

    private void initView() {
    }
}
