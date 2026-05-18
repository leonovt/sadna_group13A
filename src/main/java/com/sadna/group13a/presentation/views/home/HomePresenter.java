package com.sadna.group13a.presentation.views.home;

import com.sadna.group13a.application.Services.EventService;
import org.springframework.stereotype.Component;

@Component
public class HomePresenter {

    private final EventService eventService;

    public HomePresenter(EventService eventService) {
        this.eventService = eventService;
    }
}
