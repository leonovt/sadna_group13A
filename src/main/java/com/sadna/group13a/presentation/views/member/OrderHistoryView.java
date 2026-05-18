package com.sadna.group13a.presentation.views.member;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route("member/orders")
@PageTitle("Order History")
public class OrderHistoryView extends VerticalLayout {

    private final OrderHistoryPresenter presenter;

    public OrderHistoryView(OrderHistoryPresenter presenter) {
        this.presenter = presenter;
        initView();
    }

    private void initView() {
    }
}
