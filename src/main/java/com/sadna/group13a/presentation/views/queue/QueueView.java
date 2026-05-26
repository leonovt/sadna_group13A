package com.sadna.group13a.presentation.views.queue;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route("queue/:eventId")
@PageTitle("Waiting Room")
public class QueueView extends VerticalLayout implements BeforeEnterObserver {

    private final QueuePresenter presenter;
    private String eventId;

    public QueueView(QueuePresenter presenter) {
        this.presenter = presenter;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        eventId = event.getRouteParameters().get("eventId").orElse("");
        initView();
    }

    private void initView() {
        add(new Button("<- Home", e -> UI.getCurrent().navigate("")));
    }
}
