package com.sadna.group13a.presentation.views.event;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;

@Route("events/:eventId")
@PageTitle("Event")
public class EventDetailView extends VerticalLayout implements BeforeEnterObserver {

    private final EventDetailPresenter presenter;
    private String eventId;

    public EventDetailView(EventDetailPresenter presenter) {
        this.presenter = presenter;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        eventId = event.getRouteParameters().get("eventId").orElse("");
        initView();
    }

    private void initView() {
    }
}
