package com.sadna.group13a.presentation.views.event;

import com.sadna.group13a.application.DTO.EventDTO;
import com.sadna.group13a.application.DTO.QueueStatusDTO;
import com.sadna.group13a.application.DTO.VenueMapDTO;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.EventService;
import com.sadna.group13a.application.Services.OrderService;
import com.sadna.group13a.application.Services.QueueService;
import org.springframework.stereotype.Component;

@Component
public class EventDetailPresenter {

    private final EventService eventService;
    private final OrderService orderService;
    private final QueueService queueService;

    public EventDetailPresenter(EventService eventService, OrderService orderService,
                                QueueService queueService) {
        this.eventService = eventService;
        this.orderService = orderService;
        this.queueService = queueService;
    }

    /** Returns true if the user currently holds an active (purchasing) slot in the event's queue. */
    public boolean isUserActiveInQueue(String token, String eventId) {
        if (token == null) return false;
        Result<QueueStatusDTO> result = queueService.getStatus(token, eventId);
        return result.isSuccess() && result.getOrThrow().isActive();
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
