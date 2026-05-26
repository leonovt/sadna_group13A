package com.sadna.group13a.application.Services;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import com.sadna.group13a.application.Result;
import com.sadna.group13a.domain.Aggregates.Company.CompanyStatus;
import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import com.sadna.group13a.domain.Aggregates.Company.CompanyPermission;
import com.sadna.group13a.domain.Interfaces.ICompanyRepository;
import com.sadna.group13a.domain.Interfaces.IEventRepository;
import com.sadna.group13a.domain.Aggregates.Event.Event;
import com.sadna.group13a.domain.Aggregates.Event.EventSaleMode;
import com.sadna.group13a.domain.Aggregates.Event.SeatedZone;
import com.sadna.group13a.domain.Aggregates.Event.StandingZone;
import com.sadna.group13a.domain.Aggregates.Event.VenueMap;
import com.sadna.group13a.domain.Aggregates.Event.Zone;
import com.sadna.group13a.application.DTO.EventDTO;
import com.sadna.group13a.application.DTO.SeatDTO;
import com.sadna.group13a.application.DTO.VenueMapDTO;
import com.sadna.group13a.application.DTO.ZoneDTO;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.domain.Aggregates.OrderHistory.OrderHistory;
import com.sadna.group13a.domain.Events.EventRescheduledEvent;
import com.sadna.group13a.domain.Interfaces.IOrderHistoryRepository;
import com.sadna.group13a.domain.Interfaces.IUserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class EventService
{
    private static final Logger logger = LoggerFactory.getLogger(EventService.class);
    private final IEventRepository eventRepository;
    private final ICompanyRepository companyRepository;
    private final IAuth authGateway;
    private final IUserRepository userRepository;
    private final IOrderHistoryRepository historyRepository;
    private final ApplicationEventPublisher eventPublisher;

    public EventService(IEventRepository eventRepository, ICompanyRepository companyRepository,
                        IAuth authGateway, IUserRepository userRepository,
                        IOrderHistoryRepository historyRepository,
                        ApplicationEventPublisher eventPublisher) {
        this.eventRepository = eventRepository;
        this.companyRepository = companyRepository;
        this.authGateway = authGateway;
        this.userRepository = userRepository;
        this.historyRepository = historyRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Creates a new Event under a company.
     */
    public Result<String> createEvent(String tokenString, String companyId, String title, String description,
                                      LocalDateTime date, String category, String location)
    {
        if(!authGateway.validateToken(tokenString)) {
            logger.warn("Unauthorized createEvent attempt for company '{}'.", companyId);
            return Result.failure("Unauthorized: Invalid token.");
        }
        String initiatorId = authGateway.extractUserId(tokenString);
        Optional<ProductionCompany> compOpt = companyRepository.findById(companyId);
        if (compOpt.isEmpty()) {
            logger.warn("User '{}' tried to create event but company '{}' not found.", initiatorId, companyId);
            return Result.failure("Company not found");
        }

        ProductionCompany company = compOpt.get();
        if (!company.hasPermission(initiatorId, CompanyPermission.MANAGE_EVENTS)) {
            logger.warn("User '{}' lacks MANAGE_EVENTS permission on company '{}' — createEvent denied.", initiatorId, companyId);
            return Result.failure("User lacks permission to manage events");
        }

        Event event = new Event(UUID.randomUUID().toString(), title, description, companyId, date, category);
        event.setLocation(location);
        eventRepository.save(event);
        logger.info("User '{}' created event '{}' ('{}') under company '{}'.", initiatorId, event.getId(), title, companyId);
        return Result.success(event.getId());
    }

    /**
     * Publishes an event to the public marketplace.
     */
    public Result<Void> publishEvent(String tokenString, String eventId) {
        if(!authGateway.validateToken(tokenString)) {
            logger.warn("Unauthorized publishEvent attempt for event '{}'.", eventId);
            return Result.failure("Unauthorized: Invalid token.");
        }
        String initiatorId = authGateway.extractUserId(tokenString);
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) {
            logger.warn("User '{}' tried to publish non-existent event '{}'.", initiatorId, eventId);
            return Result.failure("Event not found");
        }

        Event event = eventOpt.get();
        Optional<ProductionCompany> compOpt = companyRepository.findById(event.getCompanyId());
        if (compOpt.isEmpty()) {
            logger.warn("User '{}' tried to publish event '{}' but company '{}' not found.", initiatorId, eventId, event.getCompanyId());
            return Result.failure("User lacks permission to publish this event");
        }
        if (!compOpt.get().hasPermission(initiatorId, CompanyPermission.MANAGE_EVENTS)) {
            logger.warn("User '{}' lacks MANAGE_EVENTS permission — publishEvent '{}' denied.", initiatorId, eventId);
            return Result.failure("User lacks permission to publish this event");
        }

        try {
            event.publish();
            eventRepository.save(event);
            logger.warn("User '{}' published event '{}'.", initiatorId, eventId);
            return Result.success();
        } catch (Exception e) {
            logger.warn("User '{}' failed to publish event '{}': {}", initiatorId, eventId, e.getMessage());
            return Result.failure(e.getMessage());
        }
    }

    /**
     * Retrieves basic event details.
     */
    public Result<EventDTO> getEvent(String token, String eventId) {
        if (token != null && !authGateway.validateToken(token)) {
            logger.warn("Unauthorized getEvent attempt for event '{}' — invalid token.", eventId);
            return Result.failure("Unauthorized: Invalid token.");
        }
        String callerId = (token != null) ? authGateway.extractUserId(token) : "anonymous";
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) {
            logger.warn("getEvent: event '{}' not found (caller='{}').", eventId, callerId);
            return Result.failure("Event not found");
        }

        Event e = eventOpt.get();
        EventDTO dto = new EventDTO(
            e.getId(),
            e.getTitle(),
            e.getDescription(),
            e.getCompanyId(),
            e.getEventDate(),
            e.getCategory(),
            e.getLocation(),
            e.isPublished(),
            e.isPublished() ? e.getTotalAvailable() : 0
        );
        logger.debug("getEvent: event '{}' retrieved by '{}'.", eventId, callerId);
        return Result.success(dto);
    }

    public Result<Void> setVenueMap(String tokenString, String eventId, VenueMap venueMap) {
        if(!authGateway.validateToken(tokenString)) {
            logger.warn("Unauthorized setVenueMap attempt for event '{}'.", eventId);
            return Result.failure("Unauthorized: Invalid token.");
        }
        String initiatorId = authGateway.extractUserId(tokenString);
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) {
            logger.warn("User '{}' tried to set venue map on non-existent event '{}'.", initiatorId, eventId);
            return Result.failure("Event not found");
        }

        Event event = eventOpt.get();
        Optional<ProductionCompany> compOpt = companyRepository.findById(event.getCompanyId());
        if (compOpt.isEmpty()) {
            logger.warn("User '{}' tried to set venue map for event '{}' but company '{}' not found.", initiatorId, eventId, event.getCompanyId());
            return Result.failure("User lacks permission to manage events");
        }
        if (!compOpt.get().hasPermission(initiatorId, CompanyPermission.MANAGE_EVENTS)) {
            logger.warn("User '{}' lacks MANAGE_EVENTS permission — setVenueMap for event '{}' denied.", initiatorId, eventId);
            return Result.failure("User lacks permission to manage events");
        }

        try {
            event.setVenueMap(venueMap);
            eventRepository.save(event);
            logger.info("User '{}' set venue map on event '{}'.", initiatorId, eventId);
            return Result.success();
        } catch (Exception e) {
            logger.warn("User '{}' failed to set venue map on event '{}': {}", initiatorId, eventId, e.getMessage());
            return Result.failure(e.getMessage());
        }
    }

    public Result<Void> unpublishEvent(String tokeString, String eventId)
    {
        if(!authGateway.validateToken(tokeString))
        {
            logger.warn("Unauthorized unpublishEvent attempt for event '{}'.", eventId);
            return Result.failure("Unauthorized: Invalid token.");
        }
        String initiatorId = authGateway.extractUserId(tokeString);
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) {
            logger.warn("User '{}' tried to unpublish non-existent event '{}'.", initiatorId, eventId);
            return Result.failure("Event not found");
        }

        Event event = eventOpt.get();
        Optional<ProductionCompany> compOpt = companyRepository.findById(event.getCompanyId());
        if (compOpt.isEmpty()) {
            logger.warn("User '{}' tried to unpublish event '{}' but company '{}' not found.", initiatorId, eventId, event.getCompanyId());
            return Result.failure("User lacks permission to manage events");
        }
        if (!compOpt.get().hasPermission(initiatorId, CompanyPermission.MANAGE_EVENTS)) {
            logger.warn("User '{}' lacks MANAGE_EVENTS permission — unpublishEvent '{}' denied.", initiatorId, eventId);
            return Result.failure("User lacks permission to manage events");
        }

        event.unpublish();
        eventRepository.save(event);
        logger.warn("User '{}' unpublished event '{}'.", initiatorId, eventId);
        return Result.success();
    }

    public Result<Void> updateEventDetails(String tokenString, String eventId, String title, String description, LocalDateTime date, String category) {
        if(!authGateway.validateToken(tokenString)) {
            logger.warn("Unauthorized updateEventDetails attempt for event '{}'.", eventId);
            return Result.failure("Unauthorized: Invalid token.");
        }
        String initiatorId = authGateway.extractUserId(tokenString);
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) {
            logger.warn("User '{}' tried to update non-existent event '{}'.", initiatorId, eventId);
            return Result.failure("Event not found");
        }

        Event event = eventOpt.get();
        Optional<ProductionCompany> compOpt = companyRepository.findById(event.getCompanyId());
        if (compOpt.isEmpty()) {
            logger.warn("User '{}' tried to update event '{}' but company '{}' not found.", initiatorId, eventId, event.getCompanyId());
            return Result.failure("User lacks permission to manage events");
        }
        if (!compOpt.get().hasPermission(initiatorId, CompanyPermission.MANAGE_EVENTS)) {
            logger.warn("User '{}' lacks MANAGE_EVENTS permission — updateEventDetails '{}' denied.", initiatorId, eventId);
            return Result.failure("User lacks permission to manage events");
        }

        try {
            if (title != null) event.setTitle(title);
            if (description != null) event.setDescription(description);
            if (category != null) event.setCategory(category);
            if (date != null) {
                LocalDateTime oldDate = event.getEventDate();
                event.setEventDate(date);
                if (!date.equals(oldDate)) {
                    List<String> buyerIds = historyRepository.findAll().stream()
                            .filter(h -> h.getItems().stream()
                                    .anyMatch(i -> i.getEventId().equals(eventId)))
                            .map(OrderHistory::getUserId)
                            .distinct()
                            .collect(Collectors.toList());
                    eventPublisher.publishEvent(
                            new EventRescheduledEvent(eventId, event.getTitle(), date, buyerIds));
                }
            }
            eventRepository.save(event);
            logger.info("User '{}' updated details for event '{}'.", initiatorId, eventId);
            return Result.success();
        } catch (Exception e) {
            logger.warn("User '{}' failed to update event '{}': {}", initiatorId, eventId, e.getMessage());
            return Result.failure(e.getMessage());
        }
    }

    public Result<Void> setSaleMode(String tokenString, String eventId, EventSaleMode saleMode) {
        if (!authGateway.validateToken(tokenString)) {
            logger.warn("Unauthorized setSaleMode attempt for event '{}'.", eventId);
            return Result.failure("Unauthorized: Invalid token.");
        }
        String initiatorId = authGateway.extractUserId(tokenString);
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) {
            logger.warn("User '{}' tried to set sale mode on non-existent event '{}'.", initiatorId, eventId);
            return Result.failure("Event not found");
        }

        Event event = eventOpt.get();
        Optional<ProductionCompany> compOpt = companyRepository.findById(event.getCompanyId());
        if (compOpt.isEmpty()) {
            logger.warn("User '{}' tried to set sale mode for event '{}' but company '{}' not found.", initiatorId, eventId, event.getCompanyId());
            return Result.failure("User lacks permission to manage events");
        }
        if (!compOpt.get().hasPermission(initiatorId, CompanyPermission.MANAGE_EVENTS)) {
            logger.warn("User '{}' lacks MANAGE_EVENTS permission — setSaleMode for event '{}' denied.", initiatorId, eventId);
            return Result.failure("User lacks permission to manage events");
        }

        try {
            event.setSaleMode(saleMode);
            eventRepository.save(event);
            logger.info("User '{}' set sale mode to '{}' for event '{}'.", initiatorId, saleMode, eventId);
            return Result.success();
        } catch (Exception e) {
            logger.warn("User '{}' failed to set sale mode for event '{}': {}", initiatorId, eventId, e.getMessage());
            return Result.failure(e.getMessage());
        }
    }

    public Result<VenueMapDTO> getVenueMap(String token, String eventId) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized getVenueMap attempt for event '{}'.", eventId);
            return Result.failure("Unauthorized: Invalid token.");
        }
        String callerId = authGateway.extractUserId(token);
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) {
            logger.warn("getVenueMap: event '{}' not found (caller='{}').", eventId, callerId);
            return Result.failure("Event not found");
        }

        Event event = eventOpt.get();
        if (event.getVenueMap() == null) {
            logger.warn("getVenueMap: event '{}' has no venue map (caller='{}').", eventId, callerId);
            return Result.failure("Event has no venue map configured");
        }

        VenueMap map = event.getVenueMap();
        List<ZoneDTO> zoneDTOs = map.getZones().stream()
                .map(this::toZoneDTO)
                .collect(Collectors.toList());

        logger.debug("getVenueMap: venue map for event '{}' retrieved by '{}'.", eventId, callerId);
        return Result.success(new VenueMapDTO(map.getId(), map.getVenueName(), zoneDTOs));
    }

    private ZoneDTO toZoneDTO(Zone zone) {
        if (zone instanceof SeatedZone sz) {
            List<SeatDTO> seatDTOs = sz.getSeats().stream()
                    .map(s -> new SeatDTO(s.getId(), s.getLabel(), s.getEffectiveStatus()))
                    .collect(Collectors.toList());
            return new ZoneDTO(zone.getId(), zone.getName(), zone.getType(), zone.getBasePrice(),
                    zone.getMaxCapacity(), zone.getAvailableSeatCount(), seatDTOs);
        } else if (zone instanceof StandingZone stz) {
            return new ZoneDTO(zone.getId(), zone.getName(), zone.getType(), zone.getBasePrice(),
                    stz.getMaxCapacity(), stz.getAvailableSeatCount(), null);
        }
        return new ZoneDTO(zone.getId(), zone.getName(), zone.getType(), zone.getBasePrice(),
                zone.getMaxCapacity(), zone.getAvailableSeatCount(), null);
    }

    public Result<List<EventDTO>> getCompanyEvents(String tokenString, String companyId) {
        if (!authGateway.validateToken(tokenString)) {
            logger.warn("Unauthorized getCompanyEvents attempt for company '{}'.", companyId);
            return Result.failure("Unauthorized: Invalid token.");
        }
        String callerId = authGateway.extractUserId(tokenString);
        Optional<ProductionCompany> compOpt = companyRepository.findById(companyId);
        if (compOpt.isEmpty()) {
            logger.warn("getCompanyEvents: company '{}' not found (caller='{}').", companyId, callerId);
            return Result.failure("Company not found");
        }
        if (!compOpt.get().hasPermission(callerId, CompanyPermission.MANAGE_EVENTS)
                && !compOpt.get().isOwner(callerId)) {
            logger.warn("getCompanyEvents: user '{}' lacks permission on company '{}'.", callerId, companyId);
            return Result.failure("User lacks permission to view company events");
        }
        List<EventDTO> dtos = eventRepository.findByCompanyId(companyId).stream()
                .map(e -> new EventDTO(
                        e.getId(), e.getTitle(), e.getDescription(), e.getCompanyId(),
                        e.getEventDate(), e.getCategory(), e.getLocation(),
                        e.isPublished(), e.isPublished() ? e.getTotalAvailable() : 0))
                .collect(Collectors.toList());
        logger.debug("getCompanyEvents: {} event(s) retrieved for company '{}' by '{}'.", dtos.size(), companyId, callerId);
        return Result.success(dtos);
    }

    public Result<List<EventDTO>> searchEvents(String query, String category,
                                               LocalDateTime fromDate, LocalDateTime toDate,
                                               Double minPrice, Double maxPrice,
                                               String location) {
        List<EventDTO> dtos = eventRepository.findAll().stream()
            .filter(Event::isPublished)
            .filter(e -> {
                Optional<ProductionCompany> comp = companyRepository.findById(e.getCompanyId());
                return comp.isPresent() && comp.get().getStatus() == CompanyStatus.ACTIVE;
            })
            .filter(e -> query == null || e.getTitle().toLowerCase().contains(query.toLowerCase())
                                       || e.getDescription().toLowerCase().contains(query.toLowerCase()))
            .filter(e -> category == null || e.getCategory().equalsIgnoreCase(category))
            .filter(e -> location == null || (e.getLocation() != null
                                       && e.getLocation().toLowerCase().contains(location.toLowerCase())))
            .filter(e -> fromDate == null || !e.getEventDate().isBefore(fromDate))
            .filter(e -> toDate == null || !e.getEventDate().isAfter(toDate))
            .filter(e -> {
                if (minPrice == null && maxPrice == null) return true;
                if (e.getVenueMap() == null) return false;
                double cheapest = e.getVenueMap().getZones().stream()
                        .mapToDouble(Zone::getBasePrice).min().orElse(Double.MAX_VALUE);
                return (minPrice == null || cheapest >= minPrice)
                    && (maxPrice == null || cheapest <= maxPrice);
            })
            .map(e -> new EventDTO(
                e.getId(), e.getTitle(), e.getDescription(), e.getCompanyId(),
                e.getEventDate(), e.getCategory(), e.getLocation(), e.isPublished(), e.getTotalAvailable()
            ))
            .collect(Collectors.toList());
        logger.debug("searchEvents: {} result(s) — query='{}' category='{}' location='{}' from='{}' to='{}' minPrice='{}' maxPrice='{}'.",
                dtos.size(), query, category, location, fromDate, toDate, minPrice, maxPrice);
        return Result.success(dtos);
    }
}
