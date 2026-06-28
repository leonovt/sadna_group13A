package com.sadna.group13a.presentation.views.event;

import com.sadna.group13a.application.DTO.EventDTO;
import com.sadna.group13a.application.DTO.QueueStatusDTO;
import com.sadna.group13a.application.DTO.RaffleDTO;
import com.sadna.group13a.application.DTO.RaffleRegistrationDTO;
import com.sadna.group13a.application.DTO.VenueMapDTO;
import com.sadna.group13a.application.DTO.WinningTicketDTO;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.EventService;
import com.sadna.group13a.application.Services.OrderService;
import com.sadna.group13a.application.Services.QueueService;
import com.sadna.group13a.application.Services.RaffleService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class EventDetailPresenter {

    private final EventService eventService;
    private final OrderService orderService;
    private final QueueService queueService;
    private final RaffleService raffleService;

    public EventDetailPresenter(EventService eventService, OrderService orderService,
                                QueueService queueService, RaffleService raffleService) {
        this.eventService = eventService;
        this.orderService = orderService;
        this.queueService = queueService;
        this.raffleService = raffleService;
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

    /** @return true if the seat was reserved, so the view can refresh the map to show it as taken. */
    public boolean addSeatedTicket(String token, String eventId, String zoneId, String seatId, EventDetailView view) {
        Result<String> result = orderService.addItemToCart(token, eventId, zoneId, seatId);
        if (result.isSuccess()) {
            view.showSuccess("Ticket added to cart!");
            return true;
        }
        view.showError(result.getErrorMessage());
        return false;
    }

    /** @return true if the tickets were reserved, so the view can refresh the map to show updated availability. */
    public boolean addStandingTickets(String token, String eventId, String zoneId, int quantity, EventDetailView view) {
        Result<String> result = orderService.addBatchItemsToCart(token, eventId, zoneId, null, quantity);
        if (result.isSuccess()) {
            view.showSuccess(quantity + " ticket(s) added to cart!");
            return true;
        }
        view.showError(result.getErrorMessage());
        return false;
    }

    /** Returns the raffle for the given event, or failure if none exists. */
    public Result<RaffleDTO> getRaffleForEvent(String token, String eventId) {
        return raffleService.getRaffleByEventId(token, eventId);
    }

    public boolean joinRaffle(String token, String raffleId, EventDetailView view) {
        Result<Void> result = raffleService.joinRaffle(token, new RaffleRegistrationDTO(raffleId));
        if (result.isSuccess()) {
            view.showSuccess("You have successfully joined the raffle! Check back after the draw.");
            return true;
        } else {
            view.showError(result.getErrorMessage());
            return false;
        }
    }

    /**
     * Returns true if the user has a valid (non-expired) winning authorization code
     * for the raffle linked to this event.
     */
    public boolean hasWonRaffle(String token, String eventId) {
        Result<RaffleDTO> raffleResult = getRaffleForEvent(token, eventId);
        if (!raffleResult.isSuccess()) return false;
        Result<WinningTicketDTO> check = raffleService.checkMyResult(token, raffleResult.getOrThrow().id());
        return check.isSuccess() && check.getOrThrow().expiresAt().isAfter(LocalDateTime.now());
    }
}
