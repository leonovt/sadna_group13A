package com.sadna.group13a.domain.Aggregates.Raffle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Raffle aggregate (Raffle root + AuthorizationCode value object).
 * No Spring, no Mockito — pure domain instantiation.
 */
class RaffleTest {

    private static final String EVENT_ID  = "event-abc";
    private static final String COMPANY_ID = "company-xyz";

    private Raffle raffle;

    @BeforeEach
    void setUp() {
        raffle = new Raffle(UUID.randomUUID().toString(), EVENT_ID, COMPANY_ID);
    }

    // ── Construction & Initial State ──────────────────────────────

    @Test
    void givenNewRaffle_thenStatusIsOpenForRegistration() {
        assertEquals(RaffleStatus.OPEN_FOR_REGISTRATION, raffle.getStatus());
        assertTrue(raffle.getParticipantUserIds().isEmpty());
        assertTrue(raffle.getWinningCodes().isEmpty());
    }

    // ── registerParticipant: positive ────────────────────────────

    @Test
    void givenOpenRaffle_whenRegisteringParticipant_thenParticipantIsAdded() {
        raffle.registerParticipant("user-1");

        assertTrue(raffle.getParticipantUserIds().contains("user-1"));
        assertEquals(1, raffle.getParticipantUserIds().size());
    }

    @Test
    void givenOpenRaffle_whenRegisteringMultipleDistinctParticipants_thenAllAreAdded() {
        raffle.registerParticipant("user-1");
        raffle.registerParticipant("user-2");
        raffle.registerParticipant("user-3");

        assertEquals(3, raffle.getParticipantUserIds().size());
    }

    // ── registerParticipant: negative ────────────────────────────

    @Test
    void givenOpenRaffle_whenRegisteringDuplicateParticipant_thenThrowsIllegalArgumentException() {
        raffle.registerParticipant("user-1");

        assertThrows(IllegalArgumentException.class, () -> raffle.registerParticipant("user-1"));
    }

    @Test
    void givenDrawnRaffle_whenRegisteringParticipant_thenThrowsIllegalStateException() {
        raffle.registerParticipant("user-1");
        raffle.executeDraw(1, 60);

        assertThrows(IllegalStateException.class, () -> raffle.registerParticipant("user-2"));
    }

    @Test
    void givenClosedRaffle_whenRegisteringParticipant_thenThrowsIllegalStateException() {
        raffle.close();

        assertThrows(IllegalStateException.class, () -> raffle.registerParticipant("user-1"));
    }

    // ── executeDraw: positive ─────────────────────────────────────

    @Test
    void givenOpenRaffleWithParticipants_whenDrawExecuted_thenStatusIsDrawnAndWinnersCreated() {
        raffle.registerParticipant("user-1");
        raffle.registerParticipant("user-2");
        raffle.registerParticipant("user-3");

        raffle.executeDraw(2, 60);

        assertEquals(RaffleStatus.DRAWN, raffle.getStatus());
        assertEquals(2, raffle.getWinningCodes().size());
    }

    @Test
    void givenFewerParticipantsThanRequestedWinners_whenDrawExecuted_thenAllParticipantsWin() {
        raffle.registerParticipant("user-1");
        raffle.registerParticipant("user-2");

        raffle.executeDraw(10, 60);

        assertEquals(2, raffle.getWinningCodes().size());
    }

    @Test
    void givenWinnerUser_whenCheckingAuthorizationCode_thenCodeIsPresent() {
        raffle.registerParticipant("user-1");
        raffle.executeDraw(1, 60);

        assertTrue(raffle.getAuthorizationCodeFor("user-1").isPresent());
    }

    @Test
    void givenNonWinner_whenCheckingAuthorizationCode_thenCodeIsEmpty() {
        raffle.registerParticipant("user-1");
        raffle.executeDraw(1, 60);

        assertTrue(raffle.getAuthorizationCodeFor("non-winner").isEmpty());
    }

    // ── executeDraw: negative ─────────────────────────────────────

    @Test
    void givenAlreadyDrawnRaffle_whenDrawExecutedAgain_thenThrowsIllegalStateException() {
        raffle.registerParticipant("user-1");
        raffle.executeDraw(1, 60);

        assertThrows(IllegalStateException.class, () -> raffle.executeDraw(1, 60));
    }

    @Test
    void givenClosedRaffle_whenDrawExecuted_thenThrowsIllegalStateException() {
        raffle.registerParticipant("user-1");
        raffle.close();

        assertThrows(IllegalStateException.class, () -> raffle.executeDraw(1, 60));
    }

    // ── close ─────────────────────────────────────────────────────

    @Test
    void givenOpenRaffle_whenClosed_thenStatusIsClosed() {
        raffle.close();

        assertEquals(RaffleStatus.CLOSED, raffle.getStatus());
    }

    // ── Immutability of returned collections ─────────────────────

    @Test
    void givenRaffle_whenModifyingReturnedParticipantSet_thenThrowsUnsupportedOperationException() {
        raffle.registerParticipant("user-1");

        assertThrows(UnsupportedOperationException.class,
                () -> raffle.getParticipantUserIds().add("attacker"));
    }

    // ══════════════════════════════════════════════════════════════
    // AuthorizationCode value object
    // ══════════════════════════════════════════════════════════════

    @Nested
    class AuthorizationCodeTests {

        @Test
        void givenValidCode_whenCheckedForCorrectUserAndEvent_thenIsValid() {
            AuthorizationCode code = new AuthorizationCode("user-1", EVENT_ID, 60);

            assertTrue(code.isValidFor("user-1", EVENT_ID));
        }

        @Test
        void givenValidCode_whenCheckedForWrongUser_thenIsInvalid() {
            AuthorizationCode code = new AuthorizationCode("user-1", EVENT_ID, 60);

            assertFalse(code.isValidFor("user-2", EVENT_ID));
        }

        @Test
        void givenValidCode_whenCheckedForWrongEvent_thenIsInvalid() {
            AuthorizationCode code = new AuthorizationCode("user-1", EVENT_ID, 60);

            assertFalse(code.isValidFor("user-1", "different-event"));
        }

        @Test
        void givenExpiredCode_whenCheckedForCorrectUserAndEvent_thenIsInvalid() {
            // validForMinutes = -1 puts expiration 1 minute in the past
            AuthorizationCode expiredCode = new AuthorizationCode("user-1", EVENT_ID, -1);

            assertFalse(expiredCode.isValidFor("user-1", EVENT_ID));
        }

        @Test
        void givenNewCode_thenCodeStringIsNotBlank() {
            AuthorizationCode code = new AuthorizationCode("user-1", EVENT_ID, 60);

            assertNotNull(code.getCode());
            assertFalse(code.getCode().isBlank());
        }
    }
}
