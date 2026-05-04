package com.sadna.group13a.domain.Aggregates.TicketQueue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the TicketQueue aggregate.
 * No Spring, no Mockito — pure domain instantiation.
 */
class TicketQueueTest {

    private static final String EVENT_ID = "event-1";
    private static final int VALID_MINUTES = 10;

    private TicketQueue queue;

    @BeforeEach
    void setUp() {
        queue = new TicketQueue(EVENT_ID, 2);
    }

    // ── Construction ──────────────────────────────────────────────

    @Test
    void givenValidParams_whenCreatingQueue_thenQueueIsEmpty() {
        assertEquals(EVENT_ID, queue.getEventId());
        assertEquals(2, queue.getMaxConcurrentUsers());
        assertEquals(0, queue.getWaitingCount());
        assertEquals(0, queue.getActiveCount());
    }

    @Test
    void givenZeroMaxConcurrent_whenCreatingQueue_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new TicketQueue(EVENT_ID, 0));
    }

    @Test
    void givenNegativeMaxConcurrent_whenCreatingQueue_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new TicketQueue(EVENT_ID, -1));
    }

    // ── joinQueue: positive ───────────────────────────────────────

    @Test
    void givenEmptyQueue_whenUserJoins_thenWaitingCountIsOne() {
        queue.joinQueue("user-1");

        assertEquals(1, queue.getWaitingCount());
        assertFalse(queue.isUserActive("user-1"));
    }

    @Test
    void givenMultipleUsers_whenAllJoin_thenWaitingCountMatchesJoiners() {
        queue.joinQueue("user-1");
        queue.joinQueue("user-2");
        queue.joinQueue("user-3");

        assertEquals(3, queue.getWaitingCount());
    }

    // ── joinQueue: negative ───────────────────────────────────────

    @Test
    void givenUserAlreadyWaiting_whenJoiningAgain_thenThrowsIllegalArgumentException() {
        queue.joinQueue("user-1");

        assertThrows(IllegalArgumentException.class, () -> queue.joinQueue("user-1"));
    }

    @Test
    void givenUserAlreadyActive_whenJoiningQueue_thenThrowsIllegalArgumentException() {
        queue.joinQueue("user-1");
        queue.processBatch(1, VALID_MINUTES);

        assertThrows(IllegalArgumentException.class, () -> queue.joinQueue("user-1"));
    }

    // ── processBatch: positive ────────────────────────────────────

    @Test
    void givenUsersWaiting_whenBatchProcessed_thenUsersAreAdmittedUpToMaxConcurrent() {
        queue.joinQueue("user-1");
        queue.joinQueue("user-2");
        queue.joinQueue("user-3");

        List<QueueTicket> granted = queue.processBatch(3, VALID_MINUTES);

        assertEquals(2, granted.size());          // max concurrent is 2
        assertEquals(2, queue.getActiveCount());
        assertEquals(1, queue.getWaitingCount());
    }

    @Test
    void givenWaitingUsers_whenBatchSmallerThanCapacity_thenOnlyBatchSizeAdmitted() {
        queue.joinQueue("user-1");
        queue.joinQueue("user-2");
        queue.joinQueue("user-3");

        List<QueueTicket> granted = queue.processBatch(1, VALID_MINUTES);

        assertEquals(1, granted.size());
        assertEquals(2, queue.getWaitingCount());
    }

    @Test
    void givenAdmittedTicket_whenHasAccess_thenHasAccessIsTrue() {
        queue.joinQueue("user-1");
        List<QueueTicket> granted = queue.processBatch(1, VALID_MINUTES);

        assertTrue(granted.get(0).hasAccess());
        assertTrue(queue.isUserActive("user-1"));
    }

    @Test
    void givenWaitingUsers_whenBatchAdmitsSome_thenRemainingPositionsDecrement() {
        queue.joinQueue("user-1");
        queue.joinQueue("user-2");
        queue.joinQueue("user-3");

        // Admit 2 (max concurrent). user-3 is now at position 1 (was at 3, 2 admitted)
        queue.processBatch(2, VALID_MINUTES);

        QueueTicket waiting = queue.getWaitingTicket("user-3").orElseThrow();
        assertEquals(1, waiting.getPositionInLine());
    }

    // ── removeActiveUser ──────────────────────────────────────────

    @Test
    void givenActiveUser_whenRemoved_thenUserIsNoLongerActive() {
        queue.joinQueue("user-1");
        queue.processBatch(1, VALID_MINUTES);
        assertTrue(queue.isUserActive("user-1"));

        queue.removeActiveUser("user-1");

        assertFalse(queue.isUserActive("user-1"));
        assertEquals(0, queue.getActiveCount());
    }

    // ── clearQueue ────────────────────────────────────────────────

    @Test
    void givenQueueWithActiveAndWaiting_whenCleared_thenBothSetsAreEmpty() {
        queue.joinQueue("user-1");
        queue.joinQueue("user-2");
        queue.joinQueue("user-3");
        queue.processBatch(2, VALID_MINUTES);

        queue.clearQueue();

        assertEquals(0, queue.getWaitingCount());
        assertEquals(0, queue.getActiveCount());
    }

    // ── adjustMaxConcurrentUsers ──────────────────────────────────

    @Test
    void givenQueue_whenMaxConcurrentAdjustedUpward_thenNewValueIsApplied() {
        queue.adjustMaxConcurrentUsers(5);

        assertEquals(5, queue.getMaxConcurrentUsers());
    }

    @Test
    void givenQueue_whenMaxConcurrentSetToZero_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> queue.adjustMaxConcurrentUsers(0));
    }

    // ── Queries ───────────────────────────────────────────────────

    @Test
    void givenActiveUser_whenGetActiveTicket_thenReturnsPresent() {
        queue.joinQueue("user-1");
        queue.processBatch(1, VALID_MINUTES);

        assertTrue(queue.getActiveTicket("user-1").isPresent());
    }

    @Test
    void givenWaitingUser_whenGetWaitingTicket_thenReturnsPresent() {
        queue.joinQueue("user-1");
        queue.joinQueue("user-2");
        queue.joinQueue("user-3");
        queue.processBatch(2, VALID_MINUTES); // admits user-1, user-2; user-3 waits

        assertTrue(queue.getWaitingTicket("user-3").isPresent());
    }

    @Test
    void givenNonExistentUser_whenGetActiveTicket_thenReturnsEmpty() {
        assertTrue(queue.getActiveTicket("ghost").isEmpty());
    }

    // ── QueueTicket position decrements ──────────────────────────

    @Test
    void givenTicketAtPosition2_whenDecrementedBy1_thenPositionIs1() {
        QueueTicket ticket = new QueueTicket("user-1", 2);
        ticket.decrementPosition(1);

        assertEquals(1, ticket.getPositionInLine());
    }

    @Test
    void givenTicketAtPosition1_whenDecrementedBy5_thenPositionStaysAt1() {
        QueueTicket ticket = new QueueTicket("user-1", 1);
        ticket.decrementPosition(5);

        assertEquals(1, ticket.getPositionInLine());
    }
}
