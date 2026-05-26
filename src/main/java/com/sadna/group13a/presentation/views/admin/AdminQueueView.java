package com.sadna.group13a.presentation.views.admin;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route("admin/queues")
@PageTitle("Queue Control")
public class AdminQueueView extends VerticalLayout {

    private final AdminQueuePresenter presenter;

    public AdminQueueView(AdminQueuePresenter presenter) {
        this.presenter = presenter;
        initView();
    }

    private void initView() {
        add(new Button("<- Home", e -> UI.getCurrent().navigate("")));
    }
}
