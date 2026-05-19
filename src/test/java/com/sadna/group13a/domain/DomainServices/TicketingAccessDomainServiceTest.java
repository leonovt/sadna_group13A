package com.sadna.group13a.domain.DomainServices;

import com.sadna.group13a.domain.Aggregates.Event.Event;
import com.sadna.group13a.domain.Aggregates.Event.EventSaleMode;
import com.sadna.group13a.domain.Aggregates.Event.StandingZone;
import com.sadna.group13a.domain.Aggregates.Event.VenueMap;
import com.sadna.group13a.domain.Aggregates.Raffle.AuthorizationCode;
import com.sadna.group13a.domain.Aggregates.TicketQueue.TicketQueue;
import com.sadna.group13a.domain.shared.PermissionDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


class TicketingAccessDomainServiceTest {

    private static final String USER_ID  = "user-1";
    private static final String EVENT_ID = "event-1";

    private TicketingAccessDomainService service;
    private Event regularEvent;
    private Event queueEvent;
    private Event raffleEvent;

    @BeforeEach
    void setUp() {
        service = new TicketingAccessDomainService();

        regularEvent = new Event(EVENT_ID, "Rock Concert", "Desc", "co-1",
                LocalDateTime.now().plusDays(7), "Music");
        // default saleMode is REGULAR — no changes needed

        queueEvent = new Event(EVENT_ID, "Rock Concert", "Desc", "co-1",
                LocalDateTime.now().plusDays(7), "Music");
        queueEvent.setSaleMode(EventSaleMode.QUEUE);

        raffleEvent = new Event(EVENT_ID, "Rock Concert", "Desc", "co-1",
                LocalDateTime.now().plusDays(7), "Music");
        raffleEvent.setSaleMode(EventSaleMode.RAFFLE);
    }

    // ══════════════════════════════════════════════════════════════
    // Event-level availability — published + future date
    // ══════════════════════════════════════════════════════════════

    @Nested
    class EventAvailabilityTests {

        private Event publishedFutureEvent;

        @BeforeEach
        void setUpPublished() {
            publishedFutureEvent = new Event(EVENT_ID, "Concert", "Desc", "co-1",
                    LocalDateTime.now().plusDays(7), "Music");
            VenueMap venueMap = new VenueMap("vm-1", "Arena",
                    List.of(new StandingZone("z-1", "GA", 50.0, 200)));
            publishedFutureEvent.setVenueMap(venueMap);
            publishedFutureEvent.publish();
        }

        @Test
        void givenPublishedFutureEvent_whenValidatingAvailability_thenNoExceptionThrown() {
            assertDoesNotThrow(() -> service.validateEventIsOpenForSale(publishedFutureEvent));
        }

        @Test
        void givenUnpublishedEvent_whenValidatingAvailability_thenThrowsPermissionDeniedException() {
            Event unpublished = new Event(EVENT_ID, "Concert", "Desc", "co-1",
                    LocalDateTime.now().plusDays(7), "Music");
            VenueMap venueMap = new VenueMap("vm-2", "Arena",
                    List.of(new StandingZone("z-2", "GA", 50.0, 200)));
            unpublished.setVenueMap(venueMap);
            // intentionally not calling publish()

            assertThrows(PermissionDeniedException.class,
                    () -> service.validateEventIsOpenForSale(unpublished));
        }

        @Test
        void givenPastEvent_whenValidatingAvailability_thenThrowsPermissionDeniedException() {
            Event pastEvent = new Event(EVENT_ID, "Concert", "Desc", "co-1",
                    LocalDateTime.now().minusDays(1), "Music");
            VenueMap venueMap = new VenueMap("vm-3", "Arena",
                    List.of(new StandingZone("z-3", "GA", 50.0, 200)));
            pastEvent.setVenueMap(venueMap);
            pastEvent.publish();

            assertThrows(PermissionDeniedException.class,
                    () -> service.validateEventIsOpenForSale(pastEvent));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // REGULAR sale mode — no restrictions
    // ══════════════════════════════════════════════════════════════

    @Nested
    class RegularSaleModeTests {

        @Test
        void givenRegularEvent_whenValidatingAccess_thenNoExceptionThrown() {
            assertDoesNotThrow(() -> service.validateAccess(regularEvent, USER_ID, null, null));
        }

        @Test
        void givenRegularEventAndUnknownUser_whenValidatingAccess_thenNoExceptionThrown() {
            assertDoesNotThrow(() -> service.validateAccess(regularEvent, "anonymous-user", null, null));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // QUEUE sale mode — user must be in the active window
    // ══════════════════════════════════════════════════════════════

    @Nested
    class QueueSaleModeTests {

        @Test
        void givenQueueEventAndUserIsActive_whenValidatingAccess_thenNoExceptionThrown() {
            TicketQueue queue = new TicketQueue(EVENT_ID, 5);
            queue.joinQueue(USER_ID);
            queue.processBatch(1, 10);

            assertDoesNotThrow(() -> service.validateAccess(queueEvent, USER_ID, queue, null));
        }

        @Test
        void givenQueueEventAndNullQueue_whenValidatingAccess_thenThrowsPermissionDeniedException() {
            assertThrows(PermissionDeniedException.class,
                    () -> service.validateAccess(queueEvent, USER_ID, null, null));
        }

        @Test
        void givenQueueEventAndUserIsWaiting_whenValidatingAccess_thenThrowsPermissionDeniedException() {
            TicketQueue queue = new TicketQueue(EVENT_ID, 5);
            queue.joinQueue(USER_ID);
            // user is in the waiting list, not yet admitted to active window

            assertThrows(PermissionDeniedException.class,
                    () -> service.validateAccess(queueEvent, USER_ID, queue, null));
        }

        @Test
        void givenQueueEventAndUserNeverJoined_whenValidatingAccess_thenThrowsPermissionDeniedException() {
            TicketQueue queue = new TicketQueue(EVENT_ID, 5);
            // USER_ID never called joinQueue

            assertThrows(PermissionDeniedException.class,
                    () -> service.validateAccess(queueEvent, USER_ID, queue, null));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // RAFFLE sale mode — user must hold a valid AuthorizationCode
    // ══════════════════════════════════════════════════════════════

    @Nested
    class RaffleSaleModeTests {

        @Test
        void givenRaffleEventAndValidCode_whenValidatingAccess_thenNoExceptionThrown() {
            AuthorizationCode validCode = new AuthorizationCode(USER_ID, EVENT_ID, 60);

            assertDoesNotThrow(() -> service.validateAccess(raffleEvent, USER_ID, null, validCode));
        }

        @Test
        void givenRaffleEventAndNullCode_whenValidatingAccess_thenThrowsPermissionDeniedException() {
            assertThrows(PermissionDeniedException.class,
                    () -> service.validateAccess(raffleEvent, USER_ID, null, null));
        }

        @Test
        void givenRaffleEventAndCodeForWrongUser_whenValidatingAccess_thenThrowsPermissionDeniedException() {
            AuthorizationCode wrongUserCode = new AuthorizationCode("other-user", EVENT_ID, 60);

            assertThrows(PermissionDeniedException.class,
                    () -> service.validateAccess(raffleEvent, USER_ID, null, wrongUserCode));
        }

        @Test
        void givenRaffleEventAndExpiredCode_whenValidatingAccess_thenThrowsPermissionDeniedException() {
            // validForMinutes = -1 puts expiresAt one minute in the past
            AuthorizationCode expiredCode = new AuthorizationCode(USER_ID, EVENT_ID, -1);

            assertThrows(PermissionDeniedException.class,
                    () -> service.validateAccess(raffleEvent, USER_ID, null, expiredCode));
        }

        @Test
        void givenRaffleEventAndCodeForWrongEvent_whenValidatingAccess_thenThrowsPermissionDeniedException() {
            AuthorizationCode wrongEventCode = new AuthorizationCode(USER_ID, "different-event", 60);

            assertThrows(PermissionDeniedException.class,
                    () -> service.validateAccess(raffleEvent, USER_ID, null, wrongEventCode));
        }
    }
}
