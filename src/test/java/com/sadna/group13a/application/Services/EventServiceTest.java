package com.sadna.group13a.application.Services;

import com.sadna.group13a.application.DTO.EventDTO;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.domain.Aggregates.Company.CompanyPermission;
import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import com.sadna.group13a.domain.Aggregates.Event.Event;
import com.sadna.group13a.domain.Aggregates.Event.Seat;
import com.sadna.group13a.domain.Aggregates.Event.SeatedZone;
import com.sadna.group13a.domain.Aggregates.Event.VenueMap;
import com.sadna.group13a.domain.Interfaces.ICompanyRepository;
import com.sadna.group13a.domain.Interfaces.IEventRepository;
import com.sadna.group13a.domain.Interfaces.IOrderHistoryRepository;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import com.sadna.group13a.domain.DomainServices.EventSearchDomainService;
import com.sadna.group13a.domain.DomainServices.VenueMapFactory;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock private IEventRepository eventRepository;
    @Mock private ICompanyRepository companyRepository;
    @Mock private IAuth authGateway;
    @Mock private IUserRepository userRepository;
    @Mock private IOrderHistoryRepository historyRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Spy  private EventSearchDomainService eventSearchDomainService = new EventSearchDomainService();
    @Spy  private VenueMapFactory venueMapFactory = new VenueMapFactory();

    @InjectMocks
    private EventService eventService;

    private static final String TOKEN      = "valid-token";
    private static final String FOUNDER_ID = "founder-1";
    private static final String COMPANY_ID = "co-1";
    private static final LocalDateTime FUTURE = LocalDateTime.now().plusDays(30);

    private ProductionCompany company;

    @BeforeEach
    void setUp() {
        // Founder has all permissions including MANAGE_EVENTS
        company = new ProductionCompany(COMPANY_ID, "Acme", "Desc", FOUNDER_ID);

        lenient().when(authGateway.validateToken(TOKEN)).thenReturn(true);
        lenient().when(authGateway.extractUserId(TOKEN)).thenReturn(FOUNDER_ID);
        lenient().when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        lenient().when(companyRepository.findAll()).thenReturn(List.of(company));
    }

    // ── createEvent ───────────────────────────────────────────────

    @Test
    void givenInvalidToken_whenCreateEvent_thenReturnsFailure() {
        when(authGateway.validateToken("bad")).thenReturn(false);

        Result<String> result = eventService.createEvent("bad", COMPANY_ID, "Concert", "Desc", FUTURE, "Music", null);

        assertFalse(result.isSuccess());
        verify(eventRepository, never()).save(any());
    }

    @Test
    void givenCompanyNotFound_whenCreateEvent_thenReturnsFailure() {
        when(companyRepository.findById("missing")).thenReturn(Optional.empty());

        Result<String> result = eventService.createEvent(TOKEN, "missing", "Concert", "Desc", FUTURE, "Music", null);

        assertFalse(result.isSuccess());
        verify(eventRepository, never()).save(any());
    }

    @Test
    void givenUserWithoutManageEventsPermission_whenCreateEvent_thenReturnsFailure() {
        when(authGateway.extractUserId(TOKEN)).thenReturn("outsider");

        // outsider is not in the company — hasPermission returns false
        Result<String> result = eventService.createEvent(TOKEN, COMPANY_ID, "Concert", "Desc", FUTURE, "Music", null);

        assertFalse(result.isSuccess());
        verify(eventRepository, never()).save(any());
    }

    @Test
    void givenFounderWithPermission_whenCreateEvent_thenEventSavedAndIdReturned() {
        Result<String> result = eventService.createEvent(TOKEN, COMPANY_ID, "Rock Night", "Desc", FUTURE, "Music", null);

        assertTrue(result.isSuccess());
        assertNotNull(result.getData().get()); // event ID returned
        verify(eventRepository).save(any(Event.class));
    }

    // ── publishEvent ──────────────────────────────────────────────

    @Test
    void givenInvalidToken_whenPublishEvent_thenReturnsFailure() {
        when(authGateway.validateToken("bad")).thenReturn(false);

        assertFalse(eventService.publishEvent("bad", "ev-1").isSuccess());
    }

    @Test
    void givenEventNotFound_whenPublishEvent_thenReturnsFailure() {
        when(eventRepository.findById("ghost")).thenReturn(Optional.empty());

        assertFalse(eventService.publishEvent(TOKEN, "ghost").isSuccess());
    }

    @Test
    void givenEventWithNoVenueMap_whenPublishEvent_thenReturnsFailure() {
        Event event = new Event("ev-1", "Concert", "Desc", COMPANY_ID, FUTURE, "Music");
        when(eventRepository.findById("ev-1")).thenReturn(Optional.of(event));

        // publish() throws DomainException because no VenueMap set
        Result<Void> result = eventService.publishEvent(TOKEN, "ev-1");

        assertFalse(result.isSuccess());
        verify(eventRepository, never()).save(any());
    }

    @Test
    void givenEventWithVenueMap_whenPublishEvent_thenEventPublishedAndSaved() {
        Event event = buildEventWithVenueMap("ev-2");
        when(eventRepository.findById("ev-2")).thenReturn(Optional.of(event));

        Result<Void> result = eventService.publishEvent(TOKEN, "ev-2");

        assertTrue(result.isSuccess());
        assertTrue(event.isPublished());
        verify(eventRepository).save(event);
    }

    // ── getEvent ──────────────────────────────────────────────────

    @Test
    void givenEventNotFound_whenGetEvent_thenReturnsFailure() {
        when(eventRepository.findById("ghost")).thenReturn(Optional.empty());

        assertFalse(eventService.getEvent(TOKEN, "ghost").isSuccess());
    }

    @Test
    void givenExistingEvent_whenGetEvent_thenReturnsDtoSuccessfully() {
        Event event = new Event("ev-3", "Jazz Night", "Desc", COMPANY_ID, FUTURE, "Music");
        when(eventRepository.findById("ev-3")).thenReturn(Optional.of(event));

        Result<EventDTO> result = eventService.getEvent(TOKEN, "ev-3");

        assertTrue(result.isSuccess());
        assertEquals("Jazz Night", result.getData().get().title());
    }

    @Test
    void givenGuestUser_whenGetEvent_thenReturnsDtoSuccessfully() {
        Event event = new Event("ev-3b", "Open Show", "Desc", COMPANY_ID, FUTURE, "Music");
        when(eventRepository.findById("ev-3b")).thenReturn(Optional.of(event));

        // No token required — public browse
        Result<EventDTO> result = eventService.getEvent(null, "ev-3b");

        assertTrue(result.isSuccess());
    }

    // ── unpublishEvent ────────────────────────────────────────────

    @Test
    void givenPublishedEvent_whenUnpublish_thenEventUnpublishedAndSaved() {
        Event event = buildEventWithVenueMap("ev-4");
        event.publish();
        when(eventRepository.findById("ev-4")).thenReturn(Optional.of(event));

        Result<Void> result = eventService.unpublishEvent(TOKEN, "ev-4");

        assertTrue(result.isSuccess());
        assertFalse(event.isPublished());
        verify(eventRepository).save(event);
    }

    // ── searchEvents ──────────────────────────────────────────────

    @Test
    void givenPublishedEvents_whenSearchByTitle_thenReturnsMatchingEvents() {
        Event jazz = buildEventWithVenueMap("ev-5");
        jazz.publish();
        Event rock = new Event("ev-6", "Rock Night", "Desc", COMPANY_ID, FUTURE, "Music");

        when(eventRepository.findAll()).thenReturn(List.of(jazz, rock));

        Result<List<EventDTO>> result = eventService.searchEvents("jazz", null, null, null, null, null, null);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getData().get().size());
    }

    @Test
    void givenGuestUser_whenSearchEvents_thenReturnsPublishedEvents() {
        Event jazz = buildEventWithVenueMap("ev-7");
        jazz.publish();
        when(eventRepository.findAll()).thenReturn(List.of(jazz));

        Result<List<EventDTO>> result = eventService.searchEvents(null, null, null, null, null, null, null);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getData().get().size());
    }

    @Test
    void givenDateRange_whenSearchEvents_thenOnlyEventsInRangeReturned() {
        LocalDateTime past   = LocalDateTime.now().minusDays(5);
        LocalDateTime soon   = LocalDateTime.now().plusDays(5);
        LocalDateTime later  = LocalDateTime.now().plusDays(60);

        Event inRange  = buildEventWithVenueMapAndDate("ev-10", soon);
        Event outRange = buildEventWithVenueMapAndDate("ev-11", later);
        inRange.publish();
        outRange.publish();

        when(eventRepository.findAll()).thenReturn(List.of(inRange, outRange));

        LocalDateTime from = past;
        LocalDateTime to   = LocalDateTime.now().plusDays(10);
        Result<List<EventDTO>> result = eventService.searchEvents(null, null, from, to, null, null, null);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getData().get().size());
        assertEquals("ev-10", result.getData().get().get(0).id());
    }

    @Test
    void givenPriceRange_whenSearchEvents_thenOnlyEventsInPriceRangeReturned() {
        Event cheap     = buildEventWithZonePrice("ev-20", 50.0);
        Event expensive = buildEventWithZonePrice("ev-21", 200.0);
        cheap.publish();
        expensive.publish();

        when(eventRepository.findAll()).thenReturn(List.of(cheap, expensive));

        Result<List<EventDTO>> result = eventService.searchEvents(null, null, null, null, 0.0, 100.0, null);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getData().get().size());
        assertEquals("ev-20", result.getData().get().get(0).id());
    }

    @Test
    void givenPriceFilter_whenNoEventsInRange_thenReturnsEmpty() {
        Event expensive = buildEventWithZonePrice("ev-30", 500.0);
        expensive.publish();

        when(eventRepository.findAll()).thenReturn(List.of(expensive));

        Result<List<EventDTO>> result = eventService.searchEvents(null, null, null, null, null, 100.0, null);

        assertTrue(result.isSuccess());
        assertEquals(0, result.getData().get().size());
    }

    @Test
    void givenLocationFilter_whenSearchEvents_thenOnlyMatchingLocationReturned() {
        Event tlv = buildEventWithVenueMap("ev-40");
        tlv.setLocation("Tel Aviv");
        tlv.publish();
        Event haifa = buildEventWithVenueMap("ev-41");
        haifa.setLocation("Haifa");
        haifa.publish();

        when(eventRepository.findAll()).thenReturn(List.of(tlv, haifa));

        Result<List<EventDTO>> result = eventService.searchEvents(null, null, null, null, null, null, "tel aviv");

        assertTrue(result.isSuccess());
        assertEquals(1, result.getData().get().size());
        assertEquals("ev-40", result.getData().get().get(0).id());
    }

    @Test
    void givenInactiveCompany_whenSearchEvents_thenItsEventsAreExcluded() {
        Event event = buildEventWithVenueMap("ev-50");
        event.publish();
        company.suspendCompany(FOUNDER_ID);

        when(eventRepository.findAll()).thenReturn(List.of(event));

        Result<List<EventDTO>> result = eventService.searchEvents(null, null, null, null, null, null, null);

        assertTrue(result.isSuccess());
        assertEquals(0, result.getData().get().size());
    }

    // ── Helpers ───────────────────────────────────────────────────

    private Event buildEventWithVenueMap(String id) {
        return buildEventWithVenueMapAndDate(id, FUTURE);
    }

    private Event buildEventWithVenueMapAndDate(String id, LocalDateTime date) {
        Event event = new Event(id, "Jazz Night", "Desc", COMPANY_ID, date, "Music");
        VenueMap vm = new VenueMap("vm-" + id, "Arena");
        vm.addZone(new SeatedZone("z-1", "VIP", 100.0, List.of(new Seat("s-1", "A-1"))));
        event.setVenueMap(vm);
        return event;
    }

    private Event buildEventWithZonePrice(String id, double price) {
        Event event = new Event(id, "Jazz Night", "Desc", COMPANY_ID, FUTURE, "Music");
        VenueMap vm = new VenueMap("vm-" + id, "Arena");
        vm.addZone(new SeatedZone("z-" + id, "Zone", price, List.of(new Seat("s-" + id, "A-1"))));
        event.setVenueMap(vm);
        return event;
    }
}
