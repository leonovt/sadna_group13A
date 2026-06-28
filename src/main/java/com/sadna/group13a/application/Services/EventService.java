package com.sadna.group13a.application.Services;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import com.sadna.group13a.application.Result;
import com.sadna.group13a.domain.Aggregates.Company.CompanyStatus;
import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import com.sadna.group13a.domain.Aggregates.Company.CompanyPermission;
import com.sadna.group13a.domain.Interfaces.ICompanyRepository;
import com.sadna.group13a.domain.Interfaces.IEventRepository;
import com.sadna.group13a.domain.Aggregates.Company.CompanyStatus;
import com.sadna.group13a.domain.Aggregates.Event.Event;
import com.sadna.group13a.domain.Aggregates.Event.EventSaleMode;
import com.sadna.group13a.domain.Aggregates.Event.Seat;
import com.sadna.group13a.domain.Aggregates.Event.SeatedZone;
import com.sadna.group13a.domain.Aggregates.Event.StandingZone;
import com.sadna.group13a.domain.Aggregates.Event.VenueMap;
import com.sadna.group13a.domain.Aggregates.Event.Zone;
import com.sadna.group13a.domain.Aggregates.Event.ZoneType;
import com.sadna.group13a.application.DTO.EventDTO;
import com.sadna.group13a.application.DTO.SeatDTO;
import com.sadna.group13a.application.DTO.VenueMapDTO;
import com.sadna.group13a.application.DTO.ZoneCreationDTO;
import com.sadna.group13a.application.DTO.ZoneDTO;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.domain.Aggregates.OrderHistory.OrderHistory;
import com.sadna.group13a.domain.DomainServices.EventSearchDomainService;
import com.sadna.group13a.domain.DomainServices.VenueMapFactory;
import com.sadna.group13a.domain.Events.EventRescheduledEvent;
import com.sadna.group13a.domain.Interfaces.IOrderHistoryRepository;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import com.sadna.group13a.domain.shared.DiscountPolicy;
import com.sadna.group13a.domain.shared.PurchasePolicy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final EventSearchDomainService eventSearchDomainService;
    private final VenueMapFactory venueMapFactory;

    public EventService(IEventRepository eventRepository, ICompanyRepository companyRepository,
                        IAuth authGateway, IUserRepository userRepository,
                        IOrderHistoryRepository historyRepository,
                        ApplicationEventPublisher eventPublisher,
                        EventSearchDomainService eventSearchDomainService,
                        VenueMapFactory venueMapFactory) {
        this.eventRepository = eventRepository;
        this.companyRepository = companyRepository;
        this.authGateway = authGateway;
        this.userRepository = userRepository;
        this.historyRepository = historyRepository;
        this.eventPublisher = eventPublisher;
        this.eventSearchDomainService = eventSearchDomainService;
        this.venueMapFactory = venueMapFactory;
    }

    /**
     * Creates a new Event under a company.
     */
    @Transactional
    public Result<String> createEvent(String tokenString, String companyId, String title, String description,
                                      LocalDateTime date, String category, String artist, String location)
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

        // A date is a mandatory event attribute (REQUIREMENTS §1). Rejecting it here keeps
        // the system out of a state where a dateless event later NPEs the views that render it.
        if (date == null) {
            logger.warn("User '{}' tried to create an event without a date in company '{}'.", initiatorId, companyId);
            return Result.failure("An event date is required.");
        }

        Event event = new Event(UUID.randomUUID().toString(), title, description, companyId, date, category);
        event.setArtist(artist);
        event.setLocation(location);
        eventRepository.save(event);
        logger.info("User '{}' created event '{}' ('{}') under company '{}'.", initiatorId, event.getId(), title, companyId);
        return Result.success(event.getId());
    }

    /**
     * Publishes an event to the public marketplace.
     */
    @Transactional
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
    @Transactional(readOnly = true)
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
            e.getArtist(),
            e.getLocation(),
            e.isPublished(),
            e.isPublished() ? e.getTotalAvailable() : 0,
            e.getSaleMode()
        );
        logger.debug("getEvent: event '{}' retrieved by '{}'.", eventId, callerId);
        return Result.success(dto);
    }

    @Transactional
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

    /**
     * Builds a venue map from primitive zone specifications and assigns it to the
     * event. This is the entry point for the organiser UI: the presentation layer
     * supplies plain values ({@link ZoneCreationDTO}) and the domain
     * {@link VenueMap}/{@link Zone}/{@link Seat} graph is assembled here, keeping
     * aggregate construction out of the views. Authorisation, the published-event
     * guard, and persistence are reused from {@link #setVenueMap}.
     */
    @Transactional
    public Result<Void> createVenueMap(String tokenString, String eventId,
                                       String venueName, List<ZoneCreationDTO> zoneSpecs) {
        if (zoneSpecs == null || zoneSpecs.isEmpty()) {
            return Result.failure("Venue map must have at least one zone");
        }

        VenueMap venueMap;
        try {
            List<Zone> zones = new ArrayList<>();
            for (ZoneCreationDTO spec : zoneSpecs) {
                if (spec == null || spec.type() == null) {
                    throw new IllegalArgumentException("Zone type must be specified");
                }
                if (spec.type() == ZoneType.SEATED && spec.rows() > 0 && spec.columns() > 0) {
                    zones.add(venueMapFactory.buildZone(spec.name(), spec.type(), spec.basePrice(), spec.rows(), spec.columns()));
                } else {
                    zones.add(venueMapFactory.buildZone(spec.name(), spec.type(), spec.basePrice(), spec.capacity()));
                }
            }
            venueMap = venueMapFactory.build(venueName, zones);
        } catch (IllegalArgumentException e) {
            logger.warn("createVenueMap: rejected venue map for event '{}': {}", eventId, e.getMessage());
            return Result.failure(e.getMessage());
        }

        return setVenueMap(tokenString, eventId, venueMap);
    }

    /**
     * Adds seats to an existing zone on a published event without revoking any sold tickets.
     * For SEATED zones, appends new {@link Seat} objects.
     * For STANDING zones, increases the max capacity.
     */
    @Transactional
    public Result<Void> addSeatsToZone(String tokenString, String eventId, String zoneId, int additionalCount) {
        if (!authGateway.validateToken(tokenString)) {
            logger.warn("Unauthorized addSeatsToZone attempt for event '{}'.", eventId);
            return Result.failure("Unauthorized: Invalid token.");
        }
        String initiatorId = authGateway.extractUserId(tokenString);
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) {
            logger.warn("User '{}' tried to add seats to non-existent event '{}'.", initiatorId, eventId);
            return Result.failure("Event not found");
        }

        Event event = eventOpt.get();
        Optional<ProductionCompany> compOpt = companyRepository.findById(event.getCompanyId());
        if (compOpt.isEmpty()) return Result.failure("User lacks permission to manage events");
        if (!compOpt.get().hasPermission(initiatorId, CompanyPermission.MANAGE_EVENTS)) {
            logger.warn("User '{}' lacks MANAGE_EVENTS — addSeatsToZone for event '{}' denied.", initiatorId, eventId);
            return Result.failure("User lacks permission to manage events");
        }
        if (additionalCount <= 0) return Result.failure("Additional count must be positive");

        try {
            Zone zone = event.getZoneById(zoneId);
            if (zone instanceof SeatedZone sz) {
                int startIndex = sz.getSeats().size() + 1;
                List<Seat> newSeats = new ArrayList<>();
                for (int i = startIndex; i < startIndex + additionalCount; i++) {
                    newSeats.add(new Seat(UUID.randomUUID().toString(), sz.getName() + " " + i));
                }
                event.addSeatsToZone(zoneId, newSeats);
            } else if (zone instanceof StandingZone) {
                event.increaseZoneCapacity(zoneId, additionalCount);
            }
            eventRepository.save(event);
            logger.info("User '{}' added {} seats/capacity to zone '{}' on event '{}'.", initiatorId, additionalCount, zoneId, eventId);
            return Result.success();
        } catch (Exception e) {
            logger.warn("User '{}' failed to add seats to zone '{}' on event '{}': {}", initiatorId, zoneId, eventId, e.getMessage());
            return Result.failure(e.getMessage());
        }
    }

    /**
     * Adds a completely new zone to the venue map of a published event.
     */
    @Transactional
    public Result<Void> addZoneToEvent(String tokenString, String eventId, ZoneCreationDTO spec) {
        if (!authGateway.validateToken(tokenString)) {
            logger.warn("Unauthorized addZoneToEvent attempt for event '{}'.", eventId);
            return Result.failure("Unauthorized: Invalid token.");
        }
        String initiatorId = authGateway.extractUserId(tokenString);
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) {
            logger.warn("User '{}' tried to add zone to non-existent event '{}'.", initiatorId, eventId);
            return Result.failure("Event not found");
        }

        Event event = eventOpt.get();
        Optional<ProductionCompany> compOpt = companyRepository.findById(event.getCompanyId());
        if (compOpt.isEmpty()) return Result.failure("User lacks permission to manage events");
        if (!compOpt.get().hasPermission(initiatorId, CompanyPermission.MANAGE_EVENTS)) {
            logger.warn("User '{}' lacks MANAGE_EVENTS — addZoneToEvent for event '{}' denied.", initiatorId, eventId);
            return Result.failure("User lacks permission to manage events");
        }
        if (spec == null || spec.type() == null) return Result.failure("Zone type must be specified");

        try {
            Zone zone;
            if (spec.type() == ZoneType.SEATED && spec.rows() > 0 && spec.columns() > 0) {
                zone = venueMapFactory.buildZone(spec.name(), spec.type(), spec.basePrice(), spec.rows(), spec.columns());
            } else {
                zone = venueMapFactory.buildZone(spec.name(), spec.type(), spec.basePrice(), spec.capacity());
            }
            event.addZoneToVenueMap(zone);
            eventRepository.save(event);
            logger.info("User '{}' added new zone '{}' to event '{}'.", initiatorId, spec.name(), eventId);
            return Result.success();
        } catch (Exception e) {
            logger.warn("User '{}' failed to add zone to event '{}': {}", initiatorId, eventId, e.getMessage());
            return Result.failure(e.getMessage());
        }
    }

    @Transactional
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

    @Transactional
    public Result<Void> updateEventDetails(String tokenString, String eventId, String title, String description, LocalDateTime date, String category, String artist) {
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
            if (artist != null) event.setArtist(artist);
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

    @Transactional
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

    @Transactional(readOnly = true)
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

    @Transactional
    public Result<Void> setPurchasePolicy(String tokenString, String eventId, PurchasePolicy policy) {
        if (!authGateway.validateToken(tokenString)) {
            logger.warn("Unauthorized setPurchasePolicy attempt for event '{}'.", eventId);
            return Result.failure("Unauthorized: Invalid token.");
        }
        String initiatorId = authGateway.extractUserId(tokenString);
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) {
            logger.warn("User '{}' tried to set purchase policy on non-existent event '{}'.", initiatorId, eventId);
            return Result.failure("Event not found");
        }

        Event event = eventOpt.get();
        Optional<ProductionCompany> compOpt = companyRepository.findById(event.getCompanyId());
        if (compOpt.isEmpty()) {
            return Result.failure("Company not found");
        }
        if (!compOpt.get().hasPermission(initiatorId, CompanyPermission.MANAGE_POLICIES)) {
            logger.warn("User '{}' lacks MANAGE_POLICIES permission — setPurchasePolicy for event '{}' denied.", initiatorId, eventId);
            return Result.failure("User lacks permission to manage policies");
        }

        try {
            event.setPurchasePolicy(policy);
            eventRepository.save(event);
            logger.info("User '{}' updated purchase policy for event '{}'.", initiatorId, eventId);
            return Result.success();
        } catch (Exception e) {
            logger.warn("User '{}' failed to set purchase policy for event '{}': {}", initiatorId, eventId, e.getMessage());
            return Result.failure(e.getMessage());
        }
    }

    @Transactional
    public Result<Void> setDiscountPolicy(String tokenString, String eventId, DiscountPolicy policy) {
        if (!authGateway.validateToken(tokenString)) {
            logger.warn("Unauthorized setDiscountPolicy attempt for event '{}'.", eventId);
            return Result.failure("Unauthorized: Invalid token.");
        }
        String initiatorId = authGateway.extractUserId(tokenString);
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) {
            logger.warn("User '{}' tried to set discount policy on non-existent event '{}'.", initiatorId, eventId);
            return Result.failure("Event not found");
        }

        Event event = eventOpt.get();
        Optional<ProductionCompany> compOpt = companyRepository.findById(event.getCompanyId());
        if (compOpt.isEmpty()) {
            return Result.failure("Company not found");
        }
        if (!compOpt.get().hasPermission(initiatorId, CompanyPermission.MANAGE_DISCOUNTS)) {
            logger.warn("User '{}' lacks MANAGE_DISCOUNTS permission — setDiscountPolicy for event '{}' denied.", initiatorId, eventId);
            return Result.failure("User lacks permission to manage discounts");
        }

        try {
            event.setDiscountPolicy(policy);
            eventRepository.save(event);
            logger.info("User '{}' updated discount policy for event '{}'.", initiatorId, eventId);
            return Result.success();
        } catch (Exception e) {
            logger.warn("User '{}' failed to set discount policy for event '{}': {}", initiatorId, eventId, e.getMessage());
            return Result.failure(e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Result<String> getPurchasePolicyDescription(String token, String eventId) {
        if (!authGateway.validateToken(token)) return Result.failure("User not authenticated.");
        return eventRepository.findById(eventId)
                .map(e -> Result.success(com.sadna.group13a.application.PolicyFormatter.describe(e.getPurchasePolicy())))
                .orElse(Result.failure("Event not found."));
    }

    @Transactional(readOnly = true)
    public Result<String> getDiscountPolicyDescription(String token, String eventId) {
        if (!authGateway.validateToken(token)) return Result.failure("User not authenticated.");
        return eventRepository.findById(eventId)
                .map(e -> Result.success(com.sadna.group13a.application.PolicyFormatter.describe(e.getDiscountPolicy())))
                .orElse(Result.failure("Event not found."));
    }

    @Transactional(readOnly = true)
    public Result<PurchasePolicy> getPurchasePolicy(String token, String eventId) {
        if (!authGateway.validateToken(token)) return Result.failure("User not authenticated.");
        return eventRepository.findById(eventId)
                .map(e -> Result.success(e.getPurchasePolicy()))
                .orElse(Result.failure("Event not found."));
    }

    @Transactional(readOnly = true)
    public Result<DiscountPolicy> getDiscountPolicy(String token, String eventId) {
        if (!authGateway.validateToken(token)) return Result.failure("User not authenticated.");
        return eventRepository.findById(eventId)
                .map(e -> Result.success(e.getDiscountPolicy()))
                .orElse(Result.failure("Event not found."));
    }

    private ZoneDTO toZoneDTO(Zone zone) {
        if (zone instanceof SeatedZone sz) {
            List<SeatDTO> seatDTOs = sz.getSeats().stream()
                    .map(s -> new SeatDTO(s.getId(), s.getLabel(), s.getEffectiveStatus()))
                    .collect(Collectors.toList());
            return new ZoneDTO(zone.getId(), zone.getName(), zone.getType(), zone.getBasePrice(),
                    zone.getMaxCapacity(), zone.getAvailableSeatCount(), seatDTOs, sz.getRows(), sz.getColumns());
        } else if (zone instanceof StandingZone stz) {
            return new ZoneDTO(zone.getId(), zone.getName(), zone.getType(), zone.getBasePrice(),
                    stz.getMaxCapacity(), stz.getAvailableSeatCount(), null, 0, 0);
        }
        return new ZoneDTO(zone.getId(), zone.getName(), zone.getType(), zone.getBasePrice(),
                zone.getMaxCapacity(), zone.getAvailableSeatCount(), null, 0, 0);
    }

    @Transactional(readOnly = true)
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
                        e.getEventDate(), e.getCategory(), e.getArtist(), e.getLocation(),
                        e.isPublished(), e.isPublished() ? e.getTotalAvailable() : 0, e.getSaleMode()))
                .collect(Collectors.toList());
        logger.debug("getCompanyEvents: {} event(s) retrieved for company '{}' by '{}'.", dtos.size(), companyId, callerId);
        return Result.success(dtos);
    }

    @Transactional(readOnly = true)
    public Result<List<EventDTO>> searchEvents(String query, String category,
                                               LocalDateTime fromDate, LocalDateTime toDate,
                                               Double minPrice, Double maxPrice,
                                               String location, String artist) {
        Map<String, ProductionCompany> companiesById = companyRepository.findAll().stream()
                .collect(Collectors.toMap(ProductionCompany::getId, c -> c));

        List<EventDTO> dtos = eventSearchDomainService
                .search(eventRepository.findAll(), companiesById, query, category, fromDate, toDate, minPrice, maxPrice, location, artist)
                .stream()
                .map(e -> new EventDTO(
                        e.getId(), e.getTitle(), e.getDescription(), e.getCompanyId(),
                        e.getEventDate(), e.getCategory(), e.getArtist(), e.getLocation(),
                        e.isPublished(), e.getTotalAvailable(), e.getSaleMode()))
                .collect(Collectors.toList());

        return Result.success(dtos);
    }
}
