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
import com.sadna.group13a.domain.Interfaces.IUserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class EventService
{
    private static final Logger logger = LoggerFactory.getLogger(EventService.class);
    private final IEventRepository eventRepository;
    private final ICompanyRepository companyRepository;
    private final IAuth authGateway;
    private final IUserRepository userRepository;


    public EventService(IEventRepository eventRepository, ICompanyRepository companyRepository, IAuth authGateway, IUserRepository userRepository) {
        this.eventRepository = eventRepository;
        this.companyRepository = companyRepository;
        this.authGateway = authGateway;
        this.userRepository = userRepository;
    }

    /**
     * Creates a new Event under a company.
     */
    public Result<String> createEvent(String tokenString, String companyId, String title, String description,
                                      LocalDateTime date, String category, String location)
    {
        if(!authGateway.validateToken(tokenString)) {
            logger.warn("Unauthorized attempt to create event with token: {}", tokenString);
            return Result.failure("Unauthorized: Invalid token.");
        }
        String initiatorId = authGateway.extractUserId(tokenString);
        Optional<ProductionCompany> compOpt = companyRepository.findById(companyId);
        if (compOpt.isEmpty()) return Result.failure("Company not found");

        ProductionCompany company = compOpt.get();
        if (!company.hasPermission(initiatorId, CompanyPermission.MANAGE_EVENTS)) {
            return Result.failure("User lacks permission to manage events");
        }

        Event event = new Event(UUID.randomUUID().toString(), title, description, companyId, date, category);
        event.setLocation(location);
        eventRepository.save(event);
        return Result.success(event.getId());
    }

    /**
     * Publishes an event to the public marketplace.
     */
    public Result<Void> publishEvent(String tokenString, String eventId) {
        if(!authGateway.validateToken(tokenString)) {
            logger.warn("Unauthorized attempt to publish event with token: {}", tokenString);
            return Result.failure("Unauthorized: Invalid token.");
        }
        String initiatorId = authGateway.extractUserId(tokenString);
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) return Result.failure("Event not found");
        
        Event event = eventOpt.get();
        Optional<ProductionCompany> compOpt = companyRepository.findById(event.getCompanyId());
        if (compOpt.isEmpty() || !compOpt.get().hasPermission(initiatorId, CompanyPermission.MANAGE_EVENTS)) {
            return Result.failure("User lacks permission to publish this event");
        }

        try {
            event.publish();
            eventRepository.save(event);
            return Result.success();
        } catch (Exception e) {
            return Result.failure(e.getMessage());
        }
    }

    /**
     * Retrieves basic event details.
     */
    public Result<EventDTO> getEvent(String token, String eventId) {
        if (token != null && !authGateway.validateToken(token)) {
            return Result.failure("Unauthorized: Invalid token.");
        }
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) return Result.failure("Event not found");
        
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
        return Result.success(dto);
    }

    public Result<Void> setVenueMap(String tokenString, String eventId, VenueMap venueMap) {
        //validation
        if(!authGateway.validateToken(tokenString)) {
            logger.warn("Unauthorized attempt to set venue map with token: {}", tokenString);
            return Result.failure("Unauthorized: Invalid token.");
        }
        String initiatorId = authGateway.extractUserId(tokenString);
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) return Result.failure("Event not found");
        
        Event event = eventOpt.get();
        Optional<ProductionCompany> compOpt = companyRepository.findById(event.getCompanyId());
        if (compOpt.isEmpty() || !compOpt.get().hasPermission(initiatorId, CompanyPermission.MANAGE_EVENTS)) {
            return Result.failure("User lacks permission to manage events");
        }
        
        try {
            event.setVenueMap(venueMap);
            eventRepository.save(event);
            return Result.success();
        } catch (Exception e) {
            return Result.failure(e.getMessage());
        }
    }

    public Result<Void> unpublishEvent(String tokeString, String eventId)
    {
        if(!authGateway.validateToken(tokeString))
        {
            logger.warn("Unauthorized attempt to unpublish event with token: {}", tokeString);
            return Result.failure("Unauthorized: Invalid token.");
        }
        String initiatorId = authGateway.extractUserId(tokeString);
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) return Result.failure("Event not found");
        
        Event event = eventOpt.get();
        Optional<ProductionCompany> compOpt = companyRepository.findById(event.getCompanyId());
        if (compOpt.isEmpty() || !compOpt.get().hasPermission(initiatorId, CompanyPermission.MANAGE_EVENTS)) {
            return Result.failure("User lacks permission to manage events");
        }
        
        event.unpublish();
        eventRepository.save(event);
        return Result.success();
    }

    public Result<Void> updateEventDetails(String tokenString, String eventId, String title, String description, LocalDateTime date, String category) {
        if(!authGateway.validateToken(tokenString)) {
            logger.warn("Unauthorized attempt to update event details with token: {}", tokenString);
            return Result.failure("Unauthorized: Invalid token.");
        }
        String initiatorId = authGateway.extractUserId(tokenString);
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) return Result.failure("Event not found");
        
        Event event = eventOpt.get();
        Optional<ProductionCompany> compOpt = companyRepository.findById(event.getCompanyId());
        if (compOpt.isEmpty() || !compOpt.get().hasPermission(initiatorId, CompanyPermission.MANAGE_EVENTS)) {
            return Result.failure("User lacks permission to manage events");
        }
        
        try {
            if (title != null) event.setTitle(title);
            if (description != null) event.setDescription(description);
            if (date != null) event.setEventDate(date);
            if (category != null) event.setCategory(category);
            eventRepository.save(event);
            return Result.success();
        } catch (Exception e) {
            return Result.failure(e.getMessage());
        }
    }

    public Result<Void> setSaleMode(String tokenString, String eventId, EventSaleMode saleMode) {
        if (!authGateway.validateToken(tokenString)) {
            logger.warn("Unauthorized attempt to set sale mode with token: {}", tokenString);
            return Result.failure("Unauthorized: Invalid token.");
        }
        String initiatorId = authGateway.extractUserId(tokenString);
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) return Result.failure("Event not found");

        Event event = eventOpt.get();
        Optional<ProductionCompany> compOpt = companyRepository.findById(event.getCompanyId());
        if (compOpt.isEmpty() || !compOpt.get().hasPermission(initiatorId, CompanyPermission.MANAGE_EVENTS)) {
            return Result.failure("User lacks permission to manage events");
        }

        try {
            event.setSaleMode(saleMode);
            eventRepository.save(event);
            return Result.success();
        } catch (Exception e) {
            return Result.failure(e.getMessage());
        }
    }

    public Result<VenueMapDTO> getVenueMap(String token, String eventId) {
        if (!authGateway.validateToken(token)) {
            return Result.failure("Unauthorized: Invalid token.");
        }
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) return Result.failure("Event not found");

        Event event = eventOpt.get();
        if (event.getVenueMap() == null) return Result.failure("Event has no venue map configured");

        VenueMap map = event.getVenueMap();
        List<ZoneDTO> zoneDTOs = map.getZones().stream()
                .map(this::toZoneDTO)
                .collect(Collectors.toList());

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
        return Result.success(dtos);
    }
}
