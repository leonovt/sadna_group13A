package com.sadna.group13a.application.service;

import com.sadna.group13a.application.dto.EventDTO;
import com.sadna.group13a.application.dto.Result;
import com.sadna.group13a.domain.company.CompanyPermission;
import com.sadna.group13a.domain.company.ICompanyRepository;
import com.sadna.group13a.domain.company.ProductionCompany;
import com.sadna.group13a.domain.event.Event;
import com.sadna.group13a.domain.event.IEventRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service for managing Events.
 * Implements UC 4.2 (Add Event), UC 4.3 (Manage Venue Map), UC 4.4 (Publish Event).
 */
public class EventService {
    private final IEventRepository eventRepository;
    private final ICompanyRepository companyRepository;

    public EventService(IEventRepository eventRepository, ICompanyRepository companyRepository) {
        this.eventRepository = eventRepository;
        this.companyRepository = companyRepository;
    }

    /**
     * Creates a new Event under a company.
     */
    public Result<String> createEvent(String companyId, String initiatorId, String title, String description, LocalDateTime date, String category) {
        Optional<ProductionCompany> compOpt = companyRepository.findById(companyId);
        if (compOpt.isEmpty()) return Result.failure("Company not found");
        
        ProductionCompany company = compOpt.get();
        if (!company.hasPermission(initiatorId, CompanyPermission.MANAGE_EVENTS)) {
            return Result.failure("User lacks permission to manage events");
        }

        Event event = new Event(UUID.randomUUID().toString(), companyId, title, description, date, category);
        eventRepository.save(event);
        return Result.success(event.getId());
    }

    /**
     * Publishes an event to the public marketplace.
     */
    public Result<Void> publishEvent(String eventId, String initiatorId) {
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
    public Result<EventDTO> getEvent(String eventId) {
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
            e.isPublished(),
            e.isPublished() ? e.getTotalAvailable() : 0
        );
        return Result.success(dto);
    }

    public Result<Void> setVenueMap(String initiatorId, String eventId, com.sadna.group13a.domain.event.VenueMap venueMap) {
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

    public Result<Void> unpublishEvent(String initiatorId, String eventId) {
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

    public Result<Void> updateEventDetails(String initiatorId, String eventId, String title, String description, LocalDateTime date, String category) {
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

    public Result<java.util.List<EventDTO>> searchEvents(String query, String category) {
        // Simple filter for V1
        java.util.List<Event> events = eventRepository.findAll();
        var dtos = events.stream()
            .filter(Event::isPublished)
            .filter(e -> query == null || e.getTitle().toLowerCase().contains(query.toLowerCase()) || e.getDescription().toLowerCase().contains(query.toLowerCase()))
            .filter(e -> category == null || e.getCategory().equalsIgnoreCase(category))
            .map(e -> new EventDTO(
                e.getId(), e.getTitle(), e.getDescription(), e.getCompanyId(),
                e.getEventDate(), e.getCategory(), e.isPublished(), e.getTotalAvailable()
            ))
            .collect(java.util.stream.Collectors.toList());
        return Result.success(dtos);
    }
}
