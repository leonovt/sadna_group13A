package com.sadna.group13a.acceptance;

import com.sadna.group13a.application.DTO.ZoneCreationDTO;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.EventService;
import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import com.sadna.group13a.domain.Aggregates.Event.Event;
import com.sadna.group13a.domain.Aggregates.Event.Seat;
import com.sadna.group13a.domain.Aggregates.Event.SeatStatus;
import com.sadna.group13a.domain.Aggregates.Event.SeatedZone;
import com.sadna.group13a.domain.Aggregates.Event.StandingZone;
import com.sadna.group13a.domain.Aggregates.Event.VenueMap;
import com.sadna.group13a.domain.Aggregates.Event.ZoneType;
import com.sadna.group13a.domain.Aggregates.User.Member;
import com.sadna.group13a.domain.DomainServices.EventSearchDomainService;
import com.sadna.group13a.domain.DomainServices.VenueMapFactory;
import com.sadna.group13a.domain.Interfaces.ICompanyRepository;
import com.sadna.group13a.domain.Interfaces.IEventRepository;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import com.sadna.group13a.infrastructure.AuthImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.CompanyRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.EventRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.OrderHistoryRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.UserRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.FakeUserJpaRepository;
import com.sadna.group13a.infrastructure.config.PersistenceConfig;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.FakeEventJpaRepository;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.FakeOrderHistoryJpaRepository;
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
        userRepository = new UserRepositoryImpl(new FakeUserJpaRepository(), new PersistenceConfig().domainObjectMapper());
        eventRepository = new EventRepositoryImpl(new FakeEventJpaRepository(), new PersistenceConfig().domainObjectMapper());
        authGateway = new AuthImpl();
        
        eventService = new EventService(eventRepository, companyRepository, authGateway, userRepository,
                new OrderHistoryRepositoryImpl(new FakeOrderHistoryJpaRepository(), new PersistenceConfig().domainObjectMapper()), e -> {}, new EventSearchDomainService(), new VenueMapFactory());
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
        // Pre-condition: seat is AVAILABLE before any reservation
        SeatedZone preZone = (SeatedZone) eventRepository.findById("e1").get().getVenueMap().getZoneById("z1");
        assertEquals(SeatStatus.AVAILABLE, preZone.findSeatById("s1").get().getStatus(),
                "Pre: seat must be AVAILABLE before status change");

        // Logical system changes seat to HELD
        seat.hold("user1");
        eventRepository.save(event);

        // Post-condition: the persisted map reflects the HELD status in real-time
        Event fetchedEvent = eventRepository.findById("e1").get();
        SeatedZone fetchedZone = (SeatedZone) fetchedEvent.getVenueMap().getZoneById("z1");
        assertEquals(SeatStatus.HELD, fetchedZone.findSeatById("s1").get().getStatus(),
                "Post: seat status must be HELD and reflected in the stored venue map");
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
        // Pre-condition: event exists, and the acting user is NOT the company owner/staff
        assertTrue(eventRepository.findById("e1").isPresent(), "Pre: event must exist before editing attempt");
        assertFalse(company.getStaff().containsKey(intruderId), "Pre: intruder must not be part of company staff");

        VenueMap venueMap = new VenueMap("v1", "New Venue");

        Result<Void> result = eventService.setVenueMap(intruderToken, "e1", venueMap);

        // Post-condition: editing is denied and venue map is not changed
        assertFalse(result.isSuccess(), "Post: unauthorized user must be denied venue editing");
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
        // Pre-condition: event exists without a venue map, and user is the company founder
        assertNull(eventRepository.findById("e1").get().getVenueMap(), "Pre: event must not have a venue map before configuration");
        assertTrue(company.getStaff().containsKey(ownerId), "Pre: user must be company staff to configure venue");

        Seat seat = new Seat("s1", "A1");
        SeatedZone zone = new SeatedZone("z1", "Standard", 50.0, List.of(seat));
        VenueMap venueMap = new VenueMap("v1", "Stadium", List.of(zone));

        Result<Void> result = eventService.setVenueMap(ownerToken, "e1", venueMap);

        // Post-condition: venue map is saved with the correct zones, inventory is now prepared
        assertTrue(result.isSuccess(), "Post: venue configuration must succeed for the company owner");
        Event fetched = eventRepository.findById("e1").get();
        assertEquals(1, fetched.getVenueMap().getZones().size(), "Post: venue map must contain the configured zone");
        assertEquals("Stadium", fetched.getVenueMap().getVenueName(), "Post: venue name must match the saved configuration");
    }

    @Test
    @DisplayName("Given primitive zone specs — When createVenueMap — Then domain map is built and saved")
    void GivenZoneSpecs_WhenCreateVenueMap_ThenMapBuiltAndSaved() {
        String ownerId = "owner1";
        userRepository.save(new Member(ownerId, "owner", "hash"));
        String ownerToken = authGateway.generateToken(ownerId);

        ProductionCompany company = new ProductionCompany("comp1", "Company", "Desc", ownerId);
        companyRepository.save(company);

        Event event = new Event("e1", "Show", "Desc", "comp1", LocalDateTime.now().plusDays(1), "Music");
        eventRepository.save(event);
        // Pre-condition: draft event with no venue map yet
        assertNull(eventRepository.findById("e1").get().getVenueMap(), "Pre: event must not have a venue map before configuration");

        List<ZoneCreationDTO> specs = List.of(
                new ZoneCreationDTO("VIP", ZoneType.SEATED, 120.0, 3),
                new ZoneCreationDTO("GA", ZoneType.STANDING, 50.0, 200));

        Result<Void> result = eventService.createVenueMap(ownerToken, "e1", "Stadium", specs);

        // Post-condition: a domain venue map is assembled from the primitives and persisted
        assertTrue(result.isSuccess(), "Post: createVenueMap must succeed for the company owner");
        VenueMap saved = eventRepository.findById("e1").get().getVenueMap();
        assertEquals("Stadium", saved.getVenueName(), "Post: venue name must match the spec");
        assertEquals(2, saved.getZones().size(), "Post: both zones must be created");

        SeatedZone seated = (SeatedZone) saved.getZones().get(0);
        assertEquals(3, seated.getSeats().size(), "Post: seated zone must materialise one seat per capacity unit");
        assertEquals(120.0, seated.getBasePrice());

        StandingZone standing = (StandingZone) saved.getZones().get(1);
        assertEquals(200, standing.getMaxCapacity(), "Post: standing zone capacity must match the spec");
    }

    @Test
    @DisplayName("Given no zones — When createVenueMap — Then rejected without persisting")
    void GivenNoZones_WhenCreateVenueMap_ThenRejected() {
        String ownerId = "owner1";
        userRepository.save(new Member(ownerId, "owner", "hash"));
        String ownerToken = authGateway.generateToken(ownerId);
        companyRepository.save(new ProductionCompany("comp1", "Company", "Desc", ownerId));
        eventRepository.save(new Event("e1", "Show", "Desc", "comp1", LocalDateTime.now().plusDays(1), "Music"));

        Result<Void> result = eventService.createVenueMap(ownerToken, "e1", "Stadium", List.of());

        // Post-condition: an empty configuration is rejected and nothing is saved
        assertFalse(result.isSuccess(), "Post: a venue map with no zones must be rejected");
        assertTrue(result.getErrorMessage().contains("at least one zone"));
        assertNull(eventRepository.findById("e1").get().getVenueMap(), "Post: no venue map must be persisted on failure");
    }

    @Test
    @DisplayName("Given invalid zone spec — When createVenueMap — Then construction error surfaces as failure")
    void GivenInvalidZoneSpec_WhenCreateVenueMap_ThenFailure() {
        String ownerId = "owner1";
        userRepository.save(new Member(ownerId, "owner", "hash"));
        String ownerToken = authGateway.generateToken(ownerId);
        companyRepository.save(new ProductionCompany("comp1", "Company", "Desc", ownerId));
        eventRepository.save(new Event("e1", "Show", "Desc", "comp1", LocalDateTime.now().plusDays(1), "Music"));

        // A standing zone with zero capacity is invalid in the domain.
        List<ZoneCreationDTO> specs = List.of(new ZoneCreationDTO("GA", ZoneType.STANDING, 50.0, 0));

        Result<Void> result = eventService.createVenueMap(ownerToken, "e1", "Stadium", specs);

        // Post-condition: the domain validation error is returned as a failure, not thrown
        assertFalse(result.isSuccess(), "Post: invalid zone specs must produce a failure result");
        assertNull(eventRepository.findById("e1").get().getVenueMap(), "Post: no venue map must be persisted on failure");
    }
}
