package com.sadna.group13a.presentation.views.company;

import com.sadna.group13a.application.Services.CompanyService;
import com.sadna.group13a.application.Services.EventService;
import org.springframework.stereotype.Component;

@Component
public class EventManagementPresenter {

    private final EventService eventService;
    private final CompanyService companyService;

    public EventManagementPresenter(EventService eventService, CompanyService companyService) {
        this.eventService = eventService;
        this.companyService = companyService;
    }
}
