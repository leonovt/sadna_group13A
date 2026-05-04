package com.sadna.group13a.application.Services;

import com.sadna.group13a.application.DTO.SystemAnalyticsDTO;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import com.sadna.group13a.domain.Aggregates.Event.Event;
import com.sadna.group13a.domain.Aggregates.Event.Seat;
import com.sadna.group13a.domain.Aggregates.Event.SeatedZone;
import com.sadna.group13a.domain.Aggregates.Event.VenueMap;
import com.sadna.group13a.domain.Aggregates.TicketQueue.TicketQueue;
import com.sadna.group13a.domain.Aggregates.User.Admin;
import com.sadna.group13a.domain.Aggregates.User.Member;
import com.sadna.group13a.domain.Events.CompanyClosedByAdminEvent;
import com.sadna.group13a.domain.Events.UserBannedEvent;
import com.sadna.group13a.domain.Interfaces.ICompanyRepository;
import com.sadna.group13a.domain.Interfaces.IEventRepository;
import com.sadna.group13a.domain.Interfaces.IOrderHistoryRepository;
import com.sadna.group13a.domain.Interfaces.IQueueRepository;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private IUserRepository userRepository;
    @Mock private IEventRepository eventRepository;
    @Mock private ICompanyRepository companyRepository;
    @Mock private IQueueRepository queueRepository;
    @Mock private IOrderHistoryRepository historyRepository;
    @Mock private IAuth authGateway;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AdminService adminService;

    private static final String ADMIN_TOKEN = "admin-token";
    private static final String ADMIN_ID    = "admin-1";

    private Admin admin;
    private Member member;

    @BeforeEach
    void setUp() {
        admin  = new Admin(ADMIN_ID, "admin", "hash");
        member = new Member("member-1", "alice", "hash");

        lenient().when(authGateway.validateToken(ADMIN_TOKEN)).thenReturn(true);
        lenient().when(authGateway.extractUserId(ADMIN_TOKEN)).thenReturn(ADMIN_ID);
        lenient().when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
    }

    // ── deactivateUser ────────────────────────────────────────────

    @Test
    void givenInvalidToken_whenDeactivateUser_thenReturnsFailure() {
        when(authGateway.validateToken("bad")).thenReturn(false);

        Result<Void> result = adminService.deactivateUser("bad", "alice");

        assertFalse(result.isSuccess());
        verify(userRepository, never()).save(any());
    }

    @Test
    void givenNonAdminCaller_whenDeactivateUser_thenReturnsFailure() {
        Member nonAdmin = new Member("non-admin", "bob", "hash");
        when(authGateway.validateToken("bob-token")).thenReturn(true);
        when(authGateway.extractUserId("bob-token")).thenReturn("non-admin");
        when(userRepository.findById("non-admin")).thenReturn(Optional.of(nonAdmin));

        Result<Void> result = adminService.deactivateUser("bob-token", "alice");

        assertFalse(result.isSuccess());
    }

    @Test
    void givenTargetNotFound_whenDeactivateUser_thenReturnsFailure() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        Result<Void> result = adminService.deactivateUser(ADMIN_TOKEN, "ghost");

        assertFalse(result.isSuccess());
    }

    @Test
    void givenTargetIsAdmin_whenDeactivateUser_thenReturnsFailure() {
        Admin anotherAdmin = new Admin("a-2", "admin2", "hash");
        when(userRepository.findByUsername("admin2")).thenReturn(Optional.of(anotherAdmin));

        Result<Void> result = adminService.deactivateUser(ADMIN_TOKEN, "admin2");

        assertFalse(result.isSuccess());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void givenAdminAndActiveMember_whenDeactivateUser_thenMemberSavedAndBanEventPublished() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(member));

        Result<Void> result = adminService.deactivateUser(ADMIN_TOKEN, "alice");

        assertTrue(result.isSuccess());
        verify(userRepository).save(member);
        verify(eventPublisher).publishEvent(any(UserBannedEvent.class));
    }

    // ── reactivateUser ────────────────────────────────────────────

    @Test
    void givenAdminAndInactiveMember_whenReactivateUser_thenMemberSaved() {
        member.deactivate();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(member));

        Result<Void> result = adminService.reactivateUser(ADMIN_TOKEN, "alice");

        assertTrue(result.isSuccess());
        verify(userRepository).save(member);
    }

    // ── cancelEventGlobally ───────────────────────────────────────

    @Test
    void givenInvalidToken_whenCancelEventGlobally_thenReturnsFailure() {
        when(authGateway.validateToken("bad")).thenReturn(false);

        assertFalse(adminService.cancelEventGlobally("bad", "ev-1").isSuccess());
    }

    @Test
    void givenEventNotFound_whenCancelEventGlobally_thenReturnsFailure() {
        when(eventRepository.findById("missing-ev")).thenReturn(Optional.empty());

        assertFalse(adminService.cancelEventGlobally(ADMIN_TOKEN, "missing-ev").isSuccess());
    }

    @Test
    void givenPublishedEvent_whenCancelEventGlobally_thenEventUnpublishedAndSaved() {
        Event event = buildPublishedEvent("ev-1", "co-1");
        when(eventRepository.findById("ev-1")).thenReturn(Optional.of(event));

        Result<Void> result = adminService.cancelEventGlobally(ADMIN_TOKEN, "ev-1");

        assertTrue(result.isSuccess());
        assertFalse(event.isPublished());
        verify(eventRepository).save(event);
    }

    // ── closeCompanyGlobally ──────────────────────────────────────

    @Test
    void givenCompanyNotFound_whenCloseCompanyGlobally_thenReturnsFailure() {
        when(companyRepository.findById("missing-co")).thenReturn(Optional.empty());

        assertFalse(adminService.closeCompanyGlobally(ADMIN_TOKEN, "missing-co").isSuccess());
    }

    @Test
    void givenExistingCompany_whenCloseCompanyGlobally_thenCompanySavedAndEventPublished() {
        ProductionCompany company = new ProductionCompany("co-1", "Acme", "Desc", "founder-1");
        when(companyRepository.findById("co-1")).thenReturn(Optional.of(company));

        Result<Void> result = adminService.closeCompanyGlobally(ADMIN_TOKEN, "co-1");

        assertTrue(result.isSuccess());
        verify(companyRepository).save(company);
        verify(eventPublisher).publishEvent(any(CompanyClosedByAdminEvent.class));
    }

    // ── clearEventQueue ───────────────────────────────────────────

    @Test
    void givenNoQueue_whenClearEventQueue_thenReturnsFailure() {
        when(queueRepository.findByEventId("ev-1")).thenReturn(Optional.empty());

        assertFalse(adminService.clearEventQueue(ADMIN_TOKEN, "ev-1").isSuccess());
    }

    @Test
    void givenQueue_whenClearEventQueue_thenQueueClearedAndSaved() {
        TicketQueue queue = new TicketQueue("ev-1", 5);
        queue.joinQueue("user-1");
        when(queueRepository.findByEventId("ev-1")).thenReturn(Optional.of(queue));

        Result<Void> result = adminService.clearEventQueue(ADMIN_TOKEN, "ev-1");

        assertTrue(result.isSuccess());
        assertEquals(0, queue.getWaitingCount());
        verify(queueRepository).save(queue);
    }

    // ── getSystemAnalytics ────────────────────────────────────────

    @Test
    void givenInvalidToken_whenGetSystemAnalytics_thenReturnsFailure() {
        when(authGateway.validateToken("bad")).thenReturn(false);

        assertFalse(adminService.getSystemAnalytics("bad").isSuccess());
    }

    @Test
    void givenAdmin_whenGetSystemAnalytics_thenReturnsDto() {
        when(userRepository.findAll()).thenReturn(List.of(admin, member));
        when(queueRepository.findAll()).thenReturn(List.of(new TicketQueue("ev-1", 5)));
        when(companyRepository.findAll()).thenReturn(
                List.of(new ProductionCompany("co-1", "Corp", "Desc", "f-1")));
        when(eventRepository.findPublished()).thenReturn(List.of());

        Result<SystemAnalyticsDTO> result = adminService.getSystemAnalytics(ADMIN_TOKEN);

        assertTrue(result.isSuccess());
        assertEquals(2, result.getData().get().totalUsers());
        assertEquals(1, result.getData().get().activeQueues());
    }

    // ── Helpers ───────────────────────────────────────────────────

    private Event buildPublishedEvent(String id, String companyId) {
        Event event = new Event(id, "Concert", "Desc", companyId, LocalDateTime.now().plusDays(7), "Music");
        VenueMap vm = new VenueMap("vm-1", "Arena");
        vm.addZone(new SeatedZone("z-1", "VIP", 100.0, List.of(new Seat("s-1", "A-1"))));
        event.setVenueMap(vm);
        event.publish();
        return event;
    }
}
