package com.sadna.group13a.application.Services;

import com.sadna.group13a.application.DTO.QueueStatusDTO;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.domain.Aggregates.Admin.Admin;
import com.sadna.group13a.domain.Aggregates.Event.Event;
import com.sadna.group13a.domain.Aggregates.TicketQueue.QueueTicket;
import com.sadna.group13a.domain.Aggregates.TicketQueue.TicketQueue;
import com.sadna.group13a.domain.Aggregates.User.Member;
import com.sadna.group13a.domain.Events.QueueTurnArrivedEvent;
import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import com.sadna.group13a.domain.Interfaces.IAdminRepository;
import com.sadna.group13a.domain.Interfaces.ICompanyRepository;
import com.sadna.group13a.domain.Interfaces.IEventRepository;
import com.sadna.group13a.domain.Interfaces.IQueueRepository;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

    @Mock private IQueueRepository queueRepository;
    @Mock private IEventRepository eventRepository;
    @Mock private ICompanyRepository companyRepository;
    @Mock private IUserRepository userRepository;
    @Mock private IAdminRepository adminRepository;
    @Mock private IAuth authGateway;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private QueueService queueService;

    private static final String USER_TOKEN  = "user-token";
    private static final String ADMIN_TOKEN = "admin-token";
    private static final String USER_ID     = "user-1";
    private static final String ADMIN_ID    = "admin-1";
    private static final String EVENT_ID    = "event-1";

    private Member activeUser;
    private Member adminMember;
    private Event activeEvent;

    @BeforeEach
    void setUp() {
        activeUser  = new Member(USER_ID, "alice", "hash");
        adminMember = new Member(ADMIN_ID, "admin", "hash");
        activeEvent = new Event(EVENT_ID, "Test Event", "Desc", "co-1",
                java.time.LocalDateTime.now().plusDays(30), "Music");

        lenient().when(authGateway.validateToken(USER_TOKEN)).thenReturn(true);
        lenient().when(authGateway.extractUserId(USER_TOKEN)).thenReturn(USER_ID);
        lenient().when(authGateway.validateToken(ADMIN_TOKEN)).thenReturn(true);
        lenient().when(authGateway.extractUserId(ADMIN_TOKEN)).thenReturn(ADMIN_ID);
        lenient().when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(adminMember));
        lenient().when(adminRepository.findByUserId(ADMIN_ID))
                .thenReturn(Optional.of(new Admin("admin-rec-1", ADMIN_ID)));
        lenient().when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(activeEvent));
        lenient().when(companyRepository.findById("co-1"))
                .thenReturn(Optional.of(new ProductionCompany("co-1", "Corp", "Desc", USER_ID)));
    }

    // ── createQueue ───────────────────────────────────────────────

    @Test
    void givenInvalidToken_whenCreateQueue_thenReturnsFailure() {
        when(authGateway.validateToken("bad")).thenReturn(false);

        assertFalse(queueService.createQueue("bad", EVENT_ID, 10).isSuccess());
        verify(queueRepository, never()).save(any());
    }

    @Test
    void givenEventNotFound_whenCreateQueue_thenReturnsFailure() {
        when(eventRepository.findById("unknown")).thenReturn(Optional.empty());

        assertFalse(queueService.createQueue(USER_TOKEN, "unknown", 10).isSuccess());
        verify(queueRepository, never()).save(any());
    }

    @Test
    void givenQueueAlreadyExists_whenCreateQueue_thenReturnsFailure() {
        when(queueRepository.findByEventId(EVENT_ID))
                .thenReturn(Optional.of(new TicketQueue(EVENT_ID, 5)));

        assertFalse(queueService.createQueue(USER_TOKEN, EVENT_ID, 10).isSuccess());
    }

    @Test
    void givenNoExistingQueue_whenCreateQueue_thenQueueSavedAndSaleModeSet() {
        when(queueRepository.findByEventId(EVENT_ID)).thenReturn(Optional.empty());

        Result<Void> result = queueService.createQueue(USER_TOKEN, EVENT_ID, 10);

        assertTrue(result.isSuccess());
        verify(queueRepository).save(any(TicketQueue.class));
        verify(eventRepository).save(activeEvent);
        assertEquals(com.sadna.group13a.domain.Aggregates.Event.EventSaleMode.QUEUE, activeEvent.getSaleMode());
    }

    // ── joinQueue (user) ──────────────────────────────────────────

    @Test
    void givenInvalidToken_whenJoinQueue_thenReturnsFailure() {
        when(authGateway.validateToken("bad")).thenReturn(false);

        assertFalse(queueService.joinQueue("bad", EVENT_ID).isSuccess());
    }

    @Test
    void givenInactiveUser_whenJoinQueue_thenReturnsFailure() {
        activeUser.deactivate();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser));

        assertFalse(queueService.joinQueue(USER_TOKEN, EVENT_ID).isSuccess());
    }

    @Test
    void givenNoQueueConfigured_whenJoinQueue_thenGrantsImmediateAccess() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser));
        when(queueRepository.findByEventId(EVENT_ID)).thenReturn(Optional.empty());

        Result<QueueStatusDTO> result = queueService.joinQueue(USER_TOKEN, EVENT_ID);

        assertTrue(result.isSuccess());
        assertTrue(result.getData().get().isActive());
    }

    @Test
    void givenQueueWithCapacity_whenUserJoins_thenGrantedAccessAndEventPublished() {
        TicketQueue queue = new TicketQueue(EVENT_ID, 5);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser));
        when(queueRepository.findByEventId(EVENT_ID)).thenReturn(Optional.of(queue));

        Result<QueueStatusDTO> result = queueService.joinQueue(USER_TOKEN, EVENT_ID);

        assertTrue(result.isSuccess());
        assertTrue(result.getData().get().isActive());
        verify(queueRepository).save(queue);
        verify(eventPublisher).publishEvent(any(QueueTurnArrivedEvent.class));
    }

    @Test
    void givenFullQueue_whenUserJoins_thenPlacedInWaitingList() {
        TicketQueue queue = new TicketQueue(EVENT_ID, 1);
        queue.joinQueue("already-active");
        queue.processBatch(1, 10); // fills the single active slot
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser));
        when(queueRepository.findByEventId(EVENT_ID)).thenReturn(Optional.of(queue));

        Result<QueueStatusDTO> result = queueService.joinQueue(USER_TOKEN, EVENT_ID);

        assertTrue(result.isSuccess());
        assertFalse(result.getData().get().isActive());
        assertTrue(result.getData().get().positionInLine() > 0);
    }

    // ── processBatch (admin) ──────────────────────────────────────

    @Test
    void givenNonAdmin_whenProcessBatch_thenReturnsFailure() {
        when(adminRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertFalse(queueService.processBatch(USER_TOKEN, EVENT_ID, 5).isSuccess());
    }

    @Test
    void givenQueueNotFound_whenProcessBatch_thenReturnsFailure() {
        when(queueRepository.findByEventId(EVENT_ID)).thenReturn(Optional.empty());

        assertFalse(queueService.processBatch(ADMIN_TOKEN, EVENT_ID, 5).isSuccess());
    }

    @Test
    void givenAdminAndWaitingUsers_whenProcessBatch_thenGrantsAccessAndPublishesEvents() {
        TicketQueue queue = new TicketQueue(EVENT_ID, 5);
        queue.joinQueue("u-A");
        queue.joinQueue("u-B");
        when(queueRepository.findByEventId(EVENT_ID)).thenReturn(Optional.of(queue));

        Result<List<QueueTicket>> result = queueService.processBatch(ADMIN_TOKEN, EVENT_ID, 2);

        assertTrue(result.isSuccess());
        assertEquals(2, result.getData().get().size());
        verify(queueRepository).save(queue);
        verify(eventPublisher, times(2)).publishEvent(any(QueueTurnArrivedEvent.class));
    }

    // ── releaseAccess ─────────────────────────────────────────────

    @Test
    void givenNoQueueForEvent_whenReleaseAccess_thenSucceedsGracefully() {
        when(queueRepository.findByEventId(EVENT_ID)).thenReturn(Optional.empty());

        assertTrue(queueService.releaseAccess(USER_TOKEN, EVENT_ID).isSuccess());
        verify(queueRepository, never()).save(any());
    }

    @Test
    void givenQueueWithActiveUser_whenReleaseAccess_thenUserRemovedAndQueueSaved() {
        TicketQueue queue = new TicketQueue(EVENT_ID, 5);
        queue.joinQueue(USER_ID);
        queue.processBatch(1, 10);
        when(queueRepository.findByEventId(EVENT_ID)).thenReturn(Optional.of(queue));

        Result<Void> result = queueService.releaseAccess(USER_TOKEN, EVENT_ID);

        assertTrue(result.isSuccess());
        assertFalse(queue.isUserActive(USER_ID));
        verify(queueRepository).save(queue);
    }
}
