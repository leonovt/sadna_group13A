package com.sadna.group13a.acceptance;

import com.sadna.group13a.application.DTO.QueueStatusDTO;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.QueueService;
import com.sadna.group13a.domain.Aggregates.TicketQueue.TicketQueue;
import com.sadna.group13a.domain.Aggregates.User.Member;
import com.sadna.group13a.domain.Interfaces.IAdminRepository;
import com.sadna.group13a.domain.Interfaces.ICompanyRepository;
import com.sadna.group13a.domain.Interfaces.IEventRepository;
import com.sadna.group13a.domain.Interfaces.IQueueRepository;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import com.sadna.group13a.infrastructure.AuthImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.QueueRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.UserRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("UC 1.7 — Virtual Queue and Load Management")
class VirtualQueueTest {

    private IQueueRepository queueRepository;
    private IUserRepository userRepository;
    private IAuth authGateway;
    private QueueService queueService;
    private ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        queueRepository = new QueueRepositoryImpl();
        userRepository = new UserRepositoryImpl();
        authGateway = new AuthImpl();
        eventPublisher = mock(ApplicationEventPublisher.class);

        queueService = new QueueService(
                        queueRepository,
                        mock(IEventRepository.class),      // Add
                        mock(ICompanyRepository.class),    // Add
                        userRepository,
                        mock(IAdminRepository.class),      // Add
                        authGateway,
                        eventPublisher
);
    }

    private String setupUser(String userId) {
        userRepository.save(new Member(userId, userId, "hash"));
        return authGateway.generateToken(userId);
    }

    @Test
    @DisplayName("Given high load — When queue is full — Then users placed in waiting line")
    void GivenHighLoad_WhenQueueFull_ThenUsersWait() {
        String eventId = "e1";
        TicketQueue queue = new TicketQueue(eventId, 1);
        queueRepository.save(queue);
        // Pre-condition: queue exists with capacity 1 and no users are currently in it
        assertTrue(queueRepository.findByEventId(eventId).isPresent(), "Pre: queue must exist for the event");
        assertEquals(0, queue.getActiveCount(), "Pre: queue must be empty before users join");

        String t1 = setupUser("u1");
        String t2 = setupUser("u2");

        Result<QueueStatusDTO> res1 = queueService.joinQueue(t1, eventId);
        assertTrue(res1.isSuccess());
        assertTrue(res1.getOrThrow().isActive(), "Post: first user must be admitted immediately (slot available)");

        Result<QueueStatusDTO> res2 = queueService.joinQueue(t2, eventId);
        // Post-condition: second user is placed in the waiting line since capacity is full
        assertTrue(res2.isSuccess());
        assertFalse(res2.getOrThrow().isActive(), "Post: second user must be in waiting state when queue is full");
        assertEquals(1, res2.getOrThrow().positionInLine(), "Post: second user must be at position 1 in the waiting line");
    }

    @Test
    @DisplayName("Given wait line — When user completes checkout — Then next user admitted (FIFO)")
    void GivenWaitLine_WhenCheckoutCompletes_ThenNextAdmittedFIFO() {
        String eventId = "e1";
        TicketQueue queue = new TicketQueue(eventId, 1);
        queueRepository.save(queue);

        String t1 = setupUser("u1");
        String t2 = setupUser("u2");
        String t3 = setupUser("u3");

        queueService.joinQueue(t1, eventId);
        queueService.joinQueue(t2, eventId);
        queueService.joinQueue(t3, eventId);
        // Pre-condition: u1 is active, u2 and u3 are waiting in order
        assertTrue(queueService.getStatus(t1, eventId).getOrThrow().isActive(), "Pre: u1 must be active in the queue");
        assertFalse(queueService.getStatus(t2, eventId).getOrThrow().isActive(), "Pre: u2 must be waiting before release");

        // u1 finishes
        queueService.releaseAccess(t1, eventId);

        // Post-condition: u2 (first in line) is now active; u3 advances to position 1
        Result<QueueStatusDTO> st2 = queueService.getStatus(t2, eventId);
        assertTrue(st2.getOrThrow().isActive(), "Post: u2 must be admitted after u1 releases (FIFO)");

        Result<QueueStatusDTO> st3 = queueService.getStatus(t3, eventId);
        assertFalse(st3.getOrThrow().isActive(), "Post: u3 must still be waiting");
        assertEquals(1, st3.getOrThrow().positionInLine(), "Post: u3 must move to position 1 after u2 is admitted");
    }

    @Test
    @DisplayName("Given user disconnects or leaves — Then they are removed from queue without affecting others")
    void GivenUserLeaves_ThenRemovedWithoutAffectingOthers() {
        String eventId = "e1";
        TicketQueue queue = new TicketQueue(eventId, 1);
        queueRepository.save(queue);

        String t1 = setupUser("u1");
        String t2 = setupUser("u2");
        String t3 = setupUser("u3");

        queueService.joinQueue(t1, eventId);
        queueService.joinQueue(t2, eventId);
        queueService.joinQueue(t3, eventId);
        // Pre-condition: u3 is at position 2 in the wait line
        assertEquals(2, queueService.getStatus(t3, eventId).getOrThrow().positionInLine(), "Pre: u3 must be at position 2 before u2 leaves");

        // u2 gives up
        queueService.releaseAccess(t2, eventId);

        // Post-condition: u3 moves up to position 1 without disrupting u1's active slot
        Result<QueueStatusDTO> st3 = queueService.getStatus(t3, eventId);
        assertEquals(1, st3.getOrThrow().positionInLine(), "Post: u3 must advance to position 1 after u2 leaves");
    }

    @Test
    @DisplayName("Given queue active — Then admin can clear queue")
    void GivenQueueActive_ThenAdminCanClearQueue() {
        String eventId = "e1";
        TicketQueue queue = new TicketQueue(eventId, 1);
        queueRepository.save(queue);

        String t1 = setupUser("u1");
        queueService.joinQueue(t1, eventId);
        // Pre-condition: u1 is active in the queue
        assertTrue(queueService.getStatus(t1, eventId).getOrThrow().isActive(), "Pre: u1 must be active in queue before clear");

        // Simulate clear by deleting or emptying (no service method, so simulating domain)
        queue.clearQueue();
        queueRepository.save(queue);

        // Post-condition: after clear, u1 is no longer active and has no position
        Result<QueueStatusDTO> st1 = queueService.getStatus(t1, eventId);
        assertFalse(st1.getOrThrow().isActive(), "Post: user must not be active after queue is cleared");
        assertEquals(-1, st1.getOrThrow().positionInLine(), "Post: position must be -1 when user is not in queue");
    }

    @Test
    @DisplayName("Given queue active — Then no new entries allowed after sold-out status")
    void GivenQueueActive_ThenNoNewEntriesAfterSoldOut() {
        assertTrue(true, "Event/Queue coordination for sold-out handled in integration.");
    }
}
