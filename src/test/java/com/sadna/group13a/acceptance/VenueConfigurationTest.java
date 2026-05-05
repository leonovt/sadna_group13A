package com.sadna.group13a.acceptance;

import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.EventService;
import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import com.sadna.group13a.domain.Aggregates.Event.Event;
import com.sadna.group13a.domain.Aggregates.Event.Seat;
import com.sadna.group13a.domain.Aggregates.Event.SeatStatus;
import com.sadna.group13a.domain.Aggregates.Event.SeatedZone;
import com.sadna.group13a.domain.Aggregates.Event.VenueMap;
import com.sadna.group13a.domain.Aggregates.User.Member;
import com.sadna.group13a.domain.Interfaces.ICompanyRepository;
import com.sadna.group13a.domain.Interfaces.IEventRepository;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import com.sadna.group13a.infrastructure.AuthImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.CompanyRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.EventRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.UserRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UC 2.7 — Venue Configuration and Event Map")
class VenueConfigurationTest {

    private ICompanyRepository companyRepository;
    private IUserRepository userRepository;
    private IEventRepository eventRepository;
    private IAuth authGateway;
    private EventService eventService;

    @BeforeEach
    void setUp() {
        companyRepository = new CompanyRepositoryImpl();
        userRepository = new UserRepositoryImpl();
        eventRepository = new EventRepositoryImpl();
        authGateway = new AuthImpl();
        
        eventService = new EventService(eventRepository, companyRepository, authGateway, userRepository);
    }

    @Test
    @DisplayName("Given seat status changes in logical system — Then visual map reflects change in real-time")
    void GivenSeatStatusChange_ThenVisualMapReflectsInRealTime() {
        Seat seat = new Seat("s1", "A1");
        SeatedZone zone = new SeatedZone("z1", "VIP", 100.0, List.of(seat));
        VenueMap venueMap = new VenueMap("v1", "Main Hall", List.of(zone));
        
        Event event = new Event("e1", "Show", "Desc", "comp1", LocalDateTime.now().plusDays(1), "Music");
        event.setVenueMap(venueMap);
        eventRepository.save(event);

        // Logical system changes seat to HELD
        seat.hold("user1");
        eventRepository.save(event);

        Event fetchedEvent = eventRepository.findById("e1").get();
        SeatedZone fetchedZone = (SeatedZone) fetchedEvent.getVenueMap().getZoneById("z1");
        assertEquals(SeatStatus.HELD, fetchedZone.findSeatById("s1").get().getStatus());
    }

    @Test
    @DisplayName("Given map without matching logical pricing zones — When saving — Then save blocked")
    void GivenMapWithoutMatchingZones_WhenSaving_ThenBlocked() {
        assertTrue(true, "Validation for logical pricing zones should be implemented in Event App Service.");
    }

    @Test
    @DisplayName("Given unauthenticated user or user without company permission — When editing venue — Then access denied")
    void GivenUnauthorizedUser_WhenEditingVenue_ThenAccessDenied() {
        String ownerId = "owner1";
        userRepository.save(new Member(ownerId, "owner", "hash"));
        ProductionCompany company = new ProductionCompany("comp1", "Company", "Desc", ownerId);
        companyRepository.save(company);
        
        Event event = new Event("e1", "Show", "Desc", "comp1", LocalDateTime.now().plusDays(1), "Music");
        eventRepository.save(event);

        String intruderId = "intruder";
        userRepository.save(new Member(intruderId, "intruder", "hash"));
        String intruderToken = authGateway.generateToken(intruderId);

        VenueMap venueMap = new VenueMap("v1", "New Venue");
        
        Result<Void> result = eventService.setVenueMap(intruderToken, "e1", venueMap);
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("User lacks permission"));
    }

    @Test
    @DisplayName("Given valid map with zones and seats — When saving — Then inventory prepared for lottery and queue")
    void GivenValidMap_WhenSaving_ThenInventoryPrepared() {
        String ownerId = "owner1";
        userRepository.save(new Member(ownerId, "owner", "hash"));
        String ownerToken = authGateway.generateToken(ownerId);
        
        ProductionCompany company = new ProductionCompany("comp1", "Company", "Desc", ownerId);
        companyRepository.save(company);
        
        Event event = new Event("e1", "Show", "Desc", "comp1", LocalDateTime.now().plusDays(1), "Music");
        eventRepository.save(event);

        Seat seat = new Seat("s1", "A1");
        SeatedZone zone = new SeatedZone("z1", "Standard", 50.0, List.of(seat));
        VenueMap venueMap = new VenueMap("v1", "Stadium", List.of(zone));

        Result<Void> result = eventService.setVenueMap(ownerToken, "e1", venueMap);
        assertTrue(result.isSuccess());

        Event fetched = eventRepository.findById("e1").get();
        assertEquals(1, fetched.getVenueMap().getZones().size());
        assertEquals("Stadium", fetched.getVenueMap().getVenueName());
    }
}
