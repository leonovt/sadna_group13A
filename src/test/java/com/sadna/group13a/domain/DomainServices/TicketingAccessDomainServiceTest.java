package com.sadna.group13a.domain.DomainServices;

import com.sadna.group13a.domain.Aggregates.Event.Event;
import com.sadna.group13a.domain.Aggregates.Event.EventSaleMode;
import com.sadna.group13a.domain.Aggregates.Raffle.AuthorizationCode;
import com.sadna.group13a.domain.Aggregates.TicketQueue.TicketQueue;
import com.sadna.group13a.domain.shared.PermissionDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

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
