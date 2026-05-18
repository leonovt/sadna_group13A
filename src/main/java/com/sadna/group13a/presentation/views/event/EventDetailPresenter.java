package com.sadna.group13a.presentation.views.event;

import com.sadna.group13a.application.Services.EventService;
import com.sadna.group13a.application.Services.OrderService;
import org.springframework.stereotype.Component;

@Component
public class EventDetailPresenter {

    private final EventService eventService;
    private final OrderService orderService;

    public EventDetailPresenter(EventService eventService, OrderService orderService) {
        this.eventService = eventService;
        this.orderService = orderService;
    }
}
