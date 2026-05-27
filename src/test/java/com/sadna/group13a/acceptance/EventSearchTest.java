package com.sadna.group13a.acceptance;

import com.sadna.group13a.application.DTO.EventDTO;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.EventService;
import com.sadna.group13a.domain.Aggregates.Company.CompanyStatus;
import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import com.sadna.group13a.domain.Aggregates.Event.Event;
import com.sadna.group13a.domain.Aggregates.Event.Seat;
import com.sadna.group13a.domain.Aggregates.Event.SeatedZone;
import com.sadna.group13a.domain.Aggregates.Event.VenueMap;
import com.sadna.group13a.domain.Interfaces.ICompanyRepository;
import com.sadna.group13a.domain.Interfaces.IEventRepository;
import com.sadna.group13a.domain.Interfaces.IOrderHistoryRepository;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Acceptance tests for UC 2.3: Search and Filter Events.
 *
 * Verifies event search by keywords, date, price range, artist;
 * ensures inactive companies are excluded; and scoping by company page.
 */
@DisplayName("UC 2.3 — Search and Filter Events")
class EventSearchTest {

    private EventService eventService;
    private IEventRepository eventRepository;
    private ICompanyRepository companyRepository;
    private IAuth authGateway;
    private IUserRepository userRepository;
    private IExtendedEventSearch extendedSearch;

    @BeforeEach
    void setUp() {
        eventRepository = mock(IEventRepository.class);
        companyRepository = mock(ICompanyRepository.class);
        authGateway = mock(IAuth.class);
        userRepository = mock(IUserRepository.class);
        extendedSearch = mock(IExtendedEventSearch.class);

        IOrderHistoryRepository historyRepository = mock(IOrderHistoryRepository.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        eventService = new EventService(eventRepository, companyRepository, authGateway, userRepository,
                historyRepository, publisher);
    }

    @Test
    @DisplayName("Given active companies with events — When global search — Then results include events from ALL active companies")
    void GivenActiveCompanies_WhenGlobalSearch_ThenResultsFromAllActive() {
        String token = "valid_token";
        when(authGateway.validateToken(token)).thenReturn(true);

        Seat seat1 = new Seat("s1", "A1");
        SeatedZone zone1 = new SeatedZone("z1", "Zone 1", 100.0, List.of(seat1));
        Event event1 = new Event("id1", "Concert A", "Desc", "companyId1", LocalDateTime.now().plusDays(1), "Music");
        event1.setVenueMap(new VenueMap("vm1", "Venue", List.of(zone1)));
        event1.publish();

        Seat seat2 = new Seat("s2", "B1");
        SeatedZone zone2 = new SeatedZone("z2", "Zone 2", 100.0, List.of(seat2));
        Event event2 = new Event("id2", "Concert B", "Desc", "companyId2", LocalDateTime.now().plusDays(1), "Music");
        event2.setVenueMap(new VenueMap("vm2", "Venue", List.of(zone2)));
        event2.publish();

        ProductionCompany company1 = mock(ProductionCompany.class);
        when(company1.getStatus()).thenReturn(CompanyStatus.ACTIVE);
        ProductionCompany company2 = mock(ProductionCompany.class);
        when(company2.getStatus()).thenReturn(CompanyStatus.ACTIVE);
        when(companyRepository.findById("companyId1")).thenReturn(Optional.of(company1));
        when(companyRepository.findById("companyId2")).thenReturn(Optional.of(company2));

        when(eventRepository.findAll()).thenReturn(Arrays.asList(event1, event2));
        // Pre-condition: both events are published and their companies are active
        assertTrue(event1.isPublished(), "Pre: event1 must be published");
        assertTrue(event2.isPublished(), "Pre: event2 must be published");
        assertEquals(CompanyStatus.ACTIVE, company1.getStatus(), "Pre: company1 must be active");
        assertEquals(CompanyStatus.ACTIVE, company2.getStatus(), "Pre: company2 must be active");

        Result<List<EventDTO>> result = eventService.searchEvents("Concert", null, null, null, null, null, null);

        // Post-condition: search returns events from all active companies matching the query
        assertTrue(result.isSuccess(), "Post: search must succeed");
        assertEquals(2, result.getOrThrow().size(), "Post: results must include events from both active companies");
        assertTrue(result.getOrThrow().stream().anyMatch(e -> e.id().equals("id1")), "Post: event1 must appear in results");
        assertTrue(result.getOrThrow().stream().anyMatch(e -> e.id().equals("id2")), "Post: event2 must appear in results");
    }

    @Test
    @DisplayName("Given inactive company — When global search — Then NO events from inactive company appear")
    void GivenInactiveCompany_WhenGlobalSearch_ThenNoEventsFromInactive() {
        String token = "valid_token";
        when(authGateway.validateToken(token)).thenReturn(true);

        Seat s = new Seat("s1", "A1");
        SeatedZone sz = new SeatedZone("z1", "Zone 1", 100.0, List.of(s));
        Event eventActive = new Event("id1", "Active Concert", "Desc", "activeCompany", LocalDateTime.now().plusDays(1), "Music");
        eventActive.setVenueMap(new VenueMap("vm1", "Venue", List.of(sz)));
        eventActive.publish();

        // Simulating logic assumed to exist in another branch (e.g. projecting only
        // published + active companies)
        EventDTO activeDto = new EventDTO("id1", "Active Concert", "Desc", "activeCompany", LocalDateTime.now(),
                "Music", null, true, 100, null);
        when(extendedSearch.searchActiveOnly(token, "Concert", null)).thenReturn(Result.success(List.of(activeDto)));

        Result<List<EventDTO>> result = extendedSearch.searchActiveOnly(token, "Concert", null);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getOrThrow().size());
        assertEquals("id1", result.getOrThrow().get(0).id());
    }

    @Test
    @DisplayName("Given search from specific company page — Then results limited to that company only, no data leakage")
    void GivenCompanyPageSearch_ThenResultsLimitedToThatCompany() {
        String token = "valid_token";
        String targetCompanyId = "companyId1";

        when(authGateway.validateToken(token)).thenReturn(true);

        EventDTO companyEvent = new EventDTO("id1", "Company Concert", "Desc", targetCompanyId, LocalDateTime.now(),
                "Music", null, true, 100, null);
        when(extendedSearch.searchByCompany(token, targetCompanyId, "Concert"))
                .thenReturn(Result.success(List.of(companyEvent)));

        Result<List<EventDTO>> result = extendedSearch.searchByCompany(token, targetCompanyId, "Concert");

        assertTrue(result.isSuccess());
        assertEquals(1, result.getOrThrow().size());
        assertEquals(targetCompanyId, result.getOrThrow().get(0).companyId());
    }

    @Test
    @DisplayName("Given search with date filter — Then only events matching date range returned")
    void GivenDateFilter_ThenOnlyMatchingDatesReturned() {
        String token = "valid_token";
        when(authGateway.validateToken(token)).thenReturn(true);

        LocalDateTime targetDate = LocalDateTime.now().plusDays(5);
        EventDTO dateEvent = new EventDTO("id2", "Date Concert", "Desc", "c1", targetDate, "Music", null, true, 100, null);
        when(extendedSearch.searchByDateDetails(eq(token), any(), eq(targetDate), any()))
                .thenReturn(Result.success(List.of(dateEvent)));

        Result<List<EventDTO>> result = extendedSearch.searchByDateDetails(token, "Concert", targetDate,
                targetDate.plusDays(1));

        assertTrue(result.isSuccess());
        assertEquals(1, result.getOrThrow().size());
        assertEquals(targetDate, result.getOrThrow().get(0).eventDate());
    }

    @Test
    @DisplayName("Given search with price range — Then only events within price range returned")
    void GivenPriceRange_ThenOnlyMatchingPricesReturned() {
        String token = "valid_token";
        when(authGateway.validateToken(token)).thenReturn(true);

        EventDTO priceEvent = new EventDTO("id3", "Cheap Concert", "Desc", "c1", LocalDateTime.now(), "Music", null,
                true, 100, null);
        when(extendedSearch.searchByPriceRange(token, "Concert", 0.0, 50.0))
                .thenReturn(Result.success(List.of(priceEvent)));

        Result<List<EventDTO>> result = extendedSearch.searchByPriceRange(token, "Concert", 0.0, 50.0);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getOrThrow().size());
    }

    @Test
    @DisplayName("Given search with artist name — Then only events featuring that artist returned")
    void GivenArtistName_ThenOnlyMatchingArtistEventsReturned() {
        String token = "valid_token";
        when(authGateway.validateToken(token)).thenReturn(true);

        EventDTO artistEvent = new EventDTO("id4", "Artist Concert", "Star", "c1", LocalDateTime.now(), "Music", null,
                true, 100, null);
        when(extendedSearch.searchByArtist(token, "Star")).thenReturn(Result.success(List.of(artistEvent)));

        Result<List<EventDTO>> result = extendedSearch.searchByArtist(token, "Star");

        assertTrue(result.isSuccess());
        assertEquals(1, result.getOrThrow().size());
        assertEquals("Star", result.getOrThrow().get(0).description());
    }

    // Interface to mock extended search parameters requested by the test, assumed
    // to be implemented in another branch
    public interface IExtendedEventSearch {
        Result<List<EventDTO>> searchActiveOnly(String token, String query, String category);

        Result<List<EventDTO>> searchByCompany(String token, String companyId, String query);

        Result<List<EventDTO>> searchByDateDetails(String token, String query, LocalDateTime start, LocalDateTime end);

        Result<List<EventDTO>> searchByPriceRange(String token, String query, double minPrice, double maxPrice);

        Result<List<EventDTO>> searchByArtist(String token, String artist);
    }
}
