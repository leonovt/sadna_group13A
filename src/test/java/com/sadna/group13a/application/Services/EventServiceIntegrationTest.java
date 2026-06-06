package com.sadna.group13a.application.Services;

import com.sadna.group13a.application.DTO.EventDTO;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import com.sadna.group13a.domain.Aggregates.Event.Event;
import com.sadna.group13a.domain.Aggregates.Event.StandingZone;
import com.sadna.group13a.domain.Aggregates.Event.VenueMap;
import com.sadna.group13a.domain.Aggregates.User.Member;
import com.sadna.group13a.domain.DomainServices.EventSearchDomainService;
import com.sadna.group13a.domain.DomainServices.VenueMapFactory;
import com.sadna.group13a.infrastructure.RepositoryImpl.CompanyRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.EventRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.OrderHistoryRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.UserRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class EventServiceIntegrationTest {

    private static final String FOUNDER_ID  = "founder-ev-001";
    private static final String COMPANY_ID  = "company-ev-001";
    private static final String OUTSIDER_ID = "outsider-ev-001";

    private EventRepositoryImpl   eventRepo;
    private CompanyRepositoryImpl companyRepo;
    private UserRepositoryImpl    userRepo;
    private MultiUserStubAuth     auth;
    private EventService          eventService;

    @BeforeEach
    void setUp() {
        eventRepo   = new EventRepositoryImpl();
        companyRepo = new CompanyRepositoryImpl();
        userRepo    = new UserRepositoryImpl();
        auth        = new MultiUserStubAuth();

        eventService = new EventService(eventRepo, companyRepo, auth, userRepo,
                new OrderHistoryRepositoryImpl(), e -> {}, new EventSearchDomainService(), new VenueMapFactory());

        seedFounderAndCompany();
    }

    private void seedFounderAndCompany() {
        userRepo.save(new Member(FOUNDER_ID, "founder", "hashed"));
        // ProductionCompany constructor makes the owner a FOUNDER with MANAGE_EVENTS permission.
        companyRepo.save(new ProductionCompany(COMPANY_ID, "Acme Events", "desc", FOUNDER_ID));
        auth.issueToken(FOUNDER_ID);
    }

    private String founderToken() {
        return auth.issueToken(FOUNDER_ID);
    }

    private VenueMap minimalVenueMap() {
        return new VenueMap("vm-ev-1", "Main Stage",
                List.of(new StandingZone("zone-ev-1", "GA", 50.0, 200)));
    }

    private Event seedPublishedEvent(String eventId) {
        Event e = new Event(eventId, "Summer Fest", "Outdoor concert",
                COMPANY_ID, LocalDateTime.now().plusDays(30), "Music");
        e.setLocation("Tel Aviv");
        e.setVenueMap(minimalVenueMap());
        e.publish();
        eventRepo.save(e);
        return e;
    }

    private Event seedUnpublishedEventWithMap(String eventId) {
        Event e = new Event(eventId, "Winter Show", "Indoor event",
                COMPANY_ID, LocalDateTime.now().plusDays(60), "Theatre");
        e.setLocation("Jerusalem");
        e.setVenueMap(minimalVenueMap());
        eventRepo.save(e);
        return e;
    }

    // ── createEvent ───────────────────────────────────────────────

    @Nested
    @DisplayName("createEvent")
    class CreateEventTests {

        @Test
        @DisplayName("Success: event is stored and its ID is returned")
        void givenFounderToken_whenCreateEvent_thenEventPersistedAndIdReturned() {
            Result<String> result = eventService.createEvent(
                    founderToken(), COMPANY_ID,
                    "Rock Night", "Loud guitars",
                    LocalDateTime.now().plusDays(10), "Music", null, "Haifa");

            assertTrue(result.isSuccess());
            String id = result.getOrThrow();
            assertNotNull(id);
            assertTrue(eventRepo.findById(id).isPresent());
        }

        @Test
        @DisplayName("Invalid token returns failure")
        void givenInvalidToken_whenCreateEvent_thenFailure() {
            assertFalse(eventService.createEvent(
                    "bad-token", COMPANY_ID, "Rock Night", "Desc",
                    LocalDateTime.now().plusDays(10), "Music", null, "Haifa").isSuccess());
        }

        @Test
        @DisplayName("Company not found returns failure")
        void givenUnknownCompany_whenCreateEvent_thenFailure() {
            assertFalse(eventService.createEvent(
                    founderToken(), "no-such-company", "Rock Night", "Desc",
                    LocalDateTime.now().plusDays(10), "Music", null, "Haifa").isSuccess());
        }

        @Test
        @DisplayName("User without MANAGE_EVENTS permission returns failure")
        void givenOutsider_whenCreateEvent_thenFailure() {
            String outsiderToken = auth.issueToken(OUTSIDER_ID);
            userRepo.save(new Member(OUTSIDER_ID, "outsider", "hashed"));

            assertFalse(eventService.createEvent(
                    outsiderToken, COMPANY_ID, "Rock Night", "Desc",
                    LocalDateTime.now().plusDays(10), "Music", null, "Haifa").isSuccess());
        }
    }

    // ── publishEvent ──────────────────────────────────────────────

    @Nested
    @DisplayName("publishEvent")
    class PublishEventTests {

        @Test
        @DisplayName("Success: event with venue map becomes published")
        void givenEventWithVenueMap_whenPublish_thenEventIsPublished() {
            seedUnpublishedEventWithMap("ev-pub-1");

            assertTrue(eventService.publishEvent(founderToken(), "ev-pub-1").isSuccess());
            assertTrue(eventRepo.findById("ev-pub-1").orElseThrow().isPublished());
        }

        @Test
        @DisplayName("Event without venue map cannot be published")
        void givenEventWithoutVenueMap_whenPublish_thenFailure() {
            Event e = new Event("ev-nomap", "Bare Event", "No map",
                    COMPANY_ID, LocalDateTime.now().plusDays(5), "Other");
            eventRepo.save(e);

            assertFalse(eventService.publishEvent(founderToken(), "ev-nomap").isSuccess());
            assertFalse(eventRepo.findById("ev-nomap").orElseThrow().isPublished());
        }

        @Test
        @DisplayName("Invalid token returns failure")
        void givenInvalidToken_whenPublish_thenFailure() {
            seedUnpublishedEventWithMap("ev-pub-2");
            assertFalse(eventService.publishEvent("bad-token", "ev-pub-2").isSuccess());
        }
    }

    // ── getEvent ──────────────────────────────────────────────────

    @Nested
    @DisplayName("getEvent")
    class GetEventTests {

        @Test
        @DisplayName("Success with valid token")
        void givenValidToken_whenGetEvent_thenDtoReturned() {
            seedPublishedEvent("ev-get-1");

            Result<EventDTO> result = eventService.getEvent(founderToken(), "ev-get-1");

            assertTrue(result.isSuccess());
            assertEquals("ev-get-1", result.getOrThrow().id());
        }

        @Test
        @DisplayName("Null token is allowed — public browsing")
        void givenNullToken_whenGetEvent_thenDtoReturned() {
            seedPublishedEvent("ev-get-2");

            assertTrue(eventService.getEvent(null, "ev-get-2").isSuccess());
        }

        @Test
        @DisplayName("Event not found returns failure")
        void givenMissingEventId_whenGetEvent_thenFailure() {
            assertFalse(eventService.getEvent(founderToken(), "no-such-event").isSuccess());
        }
    }

    // ── unpublishEvent ────────────────────────────────────────────

    @Nested
    @DisplayName("unpublishEvent")
    class UnpublishEventTests {

        @Test
        @DisplayName("Success: published event becomes unpublished")
        void givenPublishedEvent_whenUnpublish_thenEventUnpublished() {
            seedPublishedEvent("ev-un-1");

            assertTrue(eventService.unpublishEvent(founderToken(), "ev-un-1").isSuccess());
            assertFalse(eventRepo.findById("ev-un-1").orElseThrow().isPublished());
        }

        @Test
        @DisplayName("User without permission cannot unpublish")
        void givenOutsider_whenUnpublish_thenFailureAndEventStaysPublished() {
            seedPublishedEvent("ev-un-2");
            String outsiderToken = auth.issueToken(OUTSIDER_ID);

            assertFalse(eventService.unpublishEvent(outsiderToken, "ev-un-2").isSuccess());
            assertTrue(eventRepo.findById("ev-un-2").orElseThrow().isPublished());
        }
    }

    // ── updateEventDetails ────────────────────────────────────────

    @Nested
    @DisplayName("updateEventDetails")
    class UpdateEventDetailsTests {

        @Test
        @DisplayName("Success: title and description change is persisted")
        void givenValidToken_whenUpdateDetails_thenFieldsChanged() {
            seedUnpublishedEventWithMap("ev-upd-1");

            Result<Void> result = eventService.updateEventDetails(
                    founderToken(), "ev-upd-1", "New Title", "New Desc", null, null, null);

            assertTrue(result.isSuccess());
            Event stored = eventRepo.findById("ev-upd-1").orElseThrow();
            assertEquals("New Title", stored.getTitle());
            assertEquals("New Desc", stored.getDescription());
        }

        @Test
        @DisplayName("Invalid token returns failure")
        void givenInvalidToken_whenUpdateDetails_thenFailure() {
            seedUnpublishedEventWithMap("ev-upd-2");
            assertFalse(eventService.updateEventDetails(
                    "bad-token", "ev-upd-2", "X", null, null, null, null).isSuccess());
        }
    }

    // ── searchEvents ──────────────────────────────────────────────

    @Nested
    @DisplayName("searchEvents")
    class SearchEventsTests {

        @BeforeEach
        void seedSearchData() {
            Event music = new Event("ev-s1", "Jazz Night", "Smooth jazz",
                    COMPANY_ID, LocalDateTime.now().plusDays(5), "Music");
            music.setLocation("Tel Aviv");
            music.setVenueMap(minimalVenueMap());
            music.publish();
            eventRepo.save(music);

            Event theatre = new Event("ev-s2", "Hamlet", "Shakespeare",
                    COMPANY_ID, LocalDateTime.now().plusDays(10), "Theatre");
            theatre.setLocation("Jerusalem");
            theatre.setVenueMap(minimalVenueMap());
            theatre.publish();
            eventRepo.save(theatre);

            // unpublished — must never appear in results
            Event draft = new Event("ev-s3", "Secret Gig", "Private",
                    COMPANY_ID, LocalDateTime.now().plusDays(2), "Music");
            draft.setLocation("Haifa");
            eventRepo.save(draft);
        }

        @Test
        @DisplayName("Unfiltered search returns only published events")
        void whenSearchAll_thenOnlyPublishedEventsReturned() {
            Result<List<EventDTO>> result = eventService.searchEvents(
                    null, null, null, null, null, null, null, null);

            assertTrue(result.isSuccess());
            List<EventDTO> dtos = result.getOrThrow();
            assertTrue(dtos.stream().allMatch(EventDTO::isPublished));
            assertTrue(dtos.stream().noneMatch(d -> d.id().equals("ev-s3")));
            assertEquals(2, dtos.size());
        }

        @Test
        @DisplayName("Category filter returns only matching events")
        void givenCategoryFilter_whenSearch_thenOnlyCategoryMatchesReturned() {
            Result<List<EventDTO>> result = eventService.searchEvents(
                    null, "Music", null, null, null, null, null, null);

            assertTrue(result.isSuccess());
            List<EventDTO> dtos = result.getOrThrow();
            assertTrue(dtos.stream().allMatch(d -> "Music".equals(d.category())));
            assertEquals(1, dtos.size());
            assertEquals("ev-s1", dtos.get(0).id());
        }

        @Test
        @DisplayName("Location filter is case-insensitive")
        void givenLocationFilter_whenSearch_thenCaseInsensitiveMatch() {
            Result<List<EventDTO>> result = eventService.searchEvents(
                    null, null, null, null, null, null, "tel aviv", null);

            assertTrue(result.isSuccess());
            List<EventDTO> dtos = result.getOrThrow();
            assertEquals(1, dtos.size());
            assertEquals("ev-s1", dtos.get(0).id());
        }
    }

    // ── Concurrency ───────────────────────────────────────────────

    @Nested
    @DisplayName("Concurrency")
    class ConcurrencyTests {

        // publish() is idempotent after the first call — subsequent calls may succeed
        // or be a no-op; the important invariants are no exception escapes and the
        // event ends up published.
        @Test
        @DisplayName("20 threads publishing same event — no exception escapes, event ends published")
        void given20ConcurrentPublishes_thenNoExceptionEscapesAndEventIsPublished()
                throws InterruptedException {
            seedUnpublishedEventWithMap("ev-race");

            int threads = 20;
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch start = new CountDownLatch(1);
            CopyOnWriteArrayList<String> uncaughtErrors = new CopyOnWriteArrayList<>();

            ExecutorService pool = Executors.newFixedThreadPool(threads);
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        eventService.publishEvent(founderToken(), "ev-race");
                    } catch (Exception e) {
                        uncaughtErrors.add(e.getClass().getSimpleName() + ": " + e.getMessage());
                    }
                });
            }

            ready.await();
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS));

            assertTrue(uncaughtErrors.isEmpty(),
                    "No exception must escape the service layer: " + uncaughtErrors);
            assertTrue(eventRepo.findById("ev-race").orElseThrow().isPublished());
        }
    }

    // ── Test infrastructure ───────────────────────────────────────

    static class MultiUserStubAuth implements IAuth {
        private final ConcurrentHashMap<String, String> tokenToUser = new ConcurrentHashMap<>();

        String issueToken(String userId) {
            String token = "token-" + userId;
            tokenToUser.put(token, userId);
            return token;
        }

        @Override public String generateToken(String userId) { return issueToken(userId); }
        @Override public boolean validateToken(String token) { return token != null && tokenToUser.containsKey(token); }
        @Override public String extractUserId(String token)  { return tokenToUser.get(token); }
    }
}
