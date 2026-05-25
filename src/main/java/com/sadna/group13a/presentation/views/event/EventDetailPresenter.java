package com.sadna.group13a.presentation.views.event;

import com.sadna.group13a.application.DTO.EventDTO;
import com.sadna.group13a.application.DTO.VenueMapDTO;
import com.sadna.group13a.application.Result;
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

    public Result<EventDTO> loadEvent(String token, String eventId) {
        return eventService.getEvent(token, eventId);
    }

    public Result<VenueMapDTO> loadVenueMap(String token, String eventId) {
        return eventService.getVenueMap(token, eventId);
    }

    public void addSeatedTicket(String token, String eventId, String zoneId, String seatId, EventDetailView view) {
        Result<String> result = orderService.addItemToCart(token, eventId, zoneId, seatId);
        if (result.isSuccess()) {
            view.showSuccess("Ticket added to cart!");
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void addStandingTickets(String token, String eventId, String zoneId, int quantity, EventDetailView view) {
        Result<String> result = orderService.addBatchItemsToCart(token, eventId, zoneId, null, quantity);
        if (result.isSuccess()) {
            view.showSuccess(quantity + " ticket(s) added to cart!");
        } else {
            view.showError(result.getErrorMessage());
        }
    }
}
