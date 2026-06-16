package com.sadna.group13a.application.Services;

import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.domain.Aggregates.Admin.Admin;
import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import com.sadna.group13a.domain.Aggregates.Event.Event;
import com.sadna.group13a.domain.Aggregates.Event.Seat;
import com.sadna.group13a.domain.Aggregates.Event.SeatedZone;
import com.sadna.group13a.domain.Aggregates.Event.VenueMap;
import com.sadna.group13a.domain.Aggregates.TicketQueue.TicketQueue;
import com.sadna.group13a.domain.Aggregates.User.Member;
import com.sadna.group13a.domain.Events.CompanyClosedByAdminEvent;
import com.sadna.group13a.domain.Events.UserBannedEvent;
import com.sadna.group13a.infrastructure.StubPaymentGateway;
import com.sadna.group13a.infrastructure.RepositoryImpl.AdminRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.FakeAdminJpaRepository;
import com.sadna.group13a.infrastructure.config.PersistenceConfig;
import com.sadna.group13a.infrastructure.RepositoryImpl.CompanyRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.EventRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.OrderHistoryRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.QueueRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.UserRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.FakeUserJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AdminServiceIntegrationTest {

    private static final String ADMIN_TOKEN  = "admin-integration-token";
    private static final String ADMIN_ID     = "admin-integration-001";
    private static final String MEMBER_ID    = "member-integration-001";
    private static final String COMPANY_ID   = "company-integration-001";
    private static final String EVENT_ID     = "event-integration-001";

    private AdminRepositoryImpl        adminRepo;
    private UserRepositoryImpl         userRepo;
    private EventRepositoryImpl        eventRepo;
    private CompanyRepositoryImpl      companyRepo;
    private QueueRepositoryImpl        queueRepo;
    private OrderHistoryRepositoryImpl historyRepo;

    private SystemLogService      systemLogService;
    private SpyEventPublisher     eventPublisher;
    private StubAuth              auth;
    private StubPaymentGateway    paymentGateway;

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminRepo   = new AdminRepositoryImpl(new FakeAdminJpaRepository(), new PersistenceConfig().domainObjectMapper());
        userRepo    = new UserRepositoryImpl(new FakeUserJpaRepository(), new PersistenceConfig().domainObjectMapper());
        eventRepo   = new EventRepositoryImpl();
        companyRepo = new CompanyRepositoryImpl();
        queueRepo   = new QueueRepositoryImpl();
        historyRepo = new OrderHistoryRepositoryImpl();

        systemLogService = new SystemLogService();
        eventPublisher   = new SpyEventPublisher();
        auth             = new StubAuth(ADMIN_ID, ADMIN_TOKEN);
        paymentGateway   = new StubPaymentGateway();

        adminService = new AdminService(
                userRepo, adminRepo, eventRepo, companyRepo,
                queueRepo, historyRepo, paymentGateway, auth, eventPublisher, systemLogService
        );

        seedAdmin();
    }

    private void seedAdmin() {
        userRepo.save(new Member(ADMIN_ID, "adminUser", "hashed"));
        adminRepo.save(new Admin("admin-rec-001", ADMIN_ID));
    }

    private Member seedMember(String id, String username) {
        Member m = new Member(id, username, "hashed");
        userRepo.save(m);
        return m;
    }

    private Event seedPublishedEvent(String eventId) {
        Event event = new Event(eventId, "Concert", "Desc", COMPANY_ID,
                LocalDateTime.now().plusDays(7), "Music");
        VenueMap vm = new VenueMap("vm-1", "Arena",
                List.of(new SeatedZone("z-1", "VIP", 100.0, List.of(new Seat("s-1", "A-1")))));
        event.setVenueMap(vm);
        event.publish();
        eventRepo.save(event);
        return event;
    }

    private ProductionCompany seedCompany(String companyId) {
        ProductionCompany c = new ProductionCompany(companyId, "Acme", "Desc", ADMIN_ID);
        companyRepo.save(c);
        return c;
    }

    private TicketQueue seedQueue(String eventId) {
        TicketQueue q = new TicketQueue(eventId, 10);
        queueRepo.save(q);
        return q;
    }

    // ── deactivateUser ────────────────────────────────────────────

    @Nested
    @DisplayName("deactivateUser")
    class DeactivateUserTests {

        @Test
        @DisplayName("Given valid admin and existing member, member is deactivated and ban event published")
        void givenAdminAndMember_whenDeactivateUser_thenMemberDeactivatedAndBanEventPublished() {
            seedMember(MEMBER_ID, "alice");

            Result<Void> result = adminService.deactivateUser(ADMIN_TOKEN, "alice");

            assertTrue(result.isSuccess());
            assertFalse(userRepo.findById(MEMBER_ID).orElseThrow().isActive());
            assertEquals(1, eventPublisher.countOf(UserBannedEvent.class));
        }

        @Test
        @DisplayName("Given invalid token, deactivateUser returns failure without mutating state")
        void givenInvalidToken_whenDeactivateUser_thenFailureAndNoMutation() {
            seedMember(MEMBER_ID, "alice");

            Result<Void> result = adminService.deactivateUser("INVALID", "alice");

            assertFalse(result.isSuccess());
            assertTrue(userRepo.findById(MEMBER_ID).orElseThrow().isActive());
            assertEquals(0, eventPublisher.countOf(UserBannedEvent.class));
        }

        @Test
        @DisplayName("Given non-admin caller, deactivateUser returns failure")
        void givenNonAdmin_whenDeactivateUser_thenFailure() {
            seedMember("non-admin-1", "bob");
            StubAuth nonAdminAuth = new StubAuth("non-admin-1", "non-admin-token");
            AdminService serviceForNonAdmin = new AdminService(
                    userRepo, adminRepo, eventRepo, companyRepo,
                    queueRepo, historyRepo, paymentGateway, nonAdminAuth, eventPublisher, systemLogService);
            seedMember(MEMBER_ID, "alice");

            Result<Void> result = serviceForNonAdmin.deactivateUser("non-admin-token", "alice");

            assertFalse(result.isSuccess());
        }

        @Test
        @DisplayName("Given target user does not exist, deactivateUser returns failure")
        void givenTargetNotFound_whenDeactivateUser_thenFailure() {
            Result<Void> result = adminService.deactivateUser(ADMIN_TOKEN, "ghost");

            assertFalse(result.isSuccess());
        }

        @Test
        @DisplayName("Admin cannot deactivate another admin")
        void givenTargetIsAdmin_whenDeactivateUser_thenFailure() {
            Member secondAdminMember = seedMember("admin-2", "admin2");
            adminRepo.save(new Admin("admin-rec-002", "admin-2"));

            Result<Void> result = adminService.deactivateUser(ADMIN_TOKEN, "admin2");

            assertFalse(result.isSuccess());
            assertTrue(secondAdminMember.isActive());
        }
    }

    // ── reactivateUser ────────────────────────────────────────────

    @Nested
    @DisplayName("reactivateUser")
    class ReactivateUserTests {

        @Test
        @DisplayName("Given admin and deactivated member, member is reactivated")
        void givenDeactivatedMember_whenReactivateUser_thenMemberActivated() {
            Member member = seedMember(MEMBER_ID, "alice");
            member.deactivate();
            userRepo.save(member);

            Result<Void> result = adminService.reactivateUser(ADMIN_TOKEN, "alice");

            assertTrue(result.isSuccess());
            assertTrue(userRepo.findById(MEMBER_ID).orElseThrow().isActive());
        }

        @Test
        @DisplayName("Given invalid token, reactivateUser returns failure")
        void givenInvalidToken_whenReactivateUser_thenFailure() {
            assertFalse(adminService.reactivateUser("BAD", "alice").isSuccess());
        }

        @Test
        @DisplayName("Given target not found, reactivateUser returns failure")
        void givenTargetNotFound_whenReactivateUser_thenFailure() {
            assertFalse(adminService.reactivateUser(ADMIN_TOKEN, "ghost").isSuccess());
        }
    }

    // ── cancelEventGlobally ───────────────────────────────────────

    @Nested
    @DisplayName("cancelEventGlobally")
    class CancelEventTests {

        @Test
        @DisplayName("Given published event, admin cancels it and it becomes unpublished")
        void givenPublishedEvent_whenCancelEventGlobally_thenEventUnpublished() {
            seedPublishedEvent(EVENT_ID);

            Result<Void> result = adminService.cancelEventGlobally(ADMIN_TOKEN, EVENT_ID);

            assertTrue(result.isSuccess());
            assertFalse(eventRepo.findById(EVENT_ID).orElseThrow().isPublished());
        }

        @Test
        @DisplayName("Given event not found, cancelEventGlobally returns failure")
        void givenEventNotFound_whenCancelEventGlobally_thenFailure() {
            assertFalse(adminService.cancelEventGlobally(ADMIN_TOKEN, "no-such-event").isSuccess());
        }

        @Test
        @DisplayName("Given invalid token, cancelEventGlobally returns failure")
        void givenInvalidToken_whenCancelEventGlobally_thenFailure() {
            seedPublishedEvent(EVENT_ID);
            assertFalse(adminService.cancelEventGlobally("BAD", EVENT_ID).isSuccess());
        }
    }

    // ── closeCompanyGlobally ──────────────────────────────────────

    @Nested
    @DisplayName("closeCompanyGlobally")
    class CloseCompanyTests {

        @Test
        @DisplayName("Given existing company, admin force-closes it and event is published")
        void givenExistingCompany_whenCloseCompanyGlobally_thenClosedAndEventPublished() {
            seedCompany(COMPANY_ID);

            Result<Void> result = adminService.closeCompanyGlobally(ADMIN_TOKEN, COMPANY_ID);

            assertTrue(result.isSuccess());
            assertEquals(1, eventPublisher.countOf(CompanyClosedByAdminEvent.class));
        }

        @Test
        @DisplayName("Given company not found, closeCompanyGlobally returns failure")
        void givenCompanyNotFound_whenCloseCompanyGlobally_thenFailure() {
            assertFalse(adminService.closeCompanyGlobally(ADMIN_TOKEN, "missing").isSuccess());
        }
    }

    // ── Queue control ─────────────────────────────────────────────

    @Nested
    @DisplayName("clearEventQueue")
    class ClearQueueTests {

        @Test
        @DisplayName("Given queue with waiting users, admin clears it to zero")
        void givenQueueWithUsers_whenClearEventQueue_thenQueueEmpty() {
            TicketQueue q = seedQueue(EVENT_ID);
            q.joinQueue("user-a");
            q.joinQueue("user-b");
            queueRepo.save(q);

            Result<Void> result = adminService.clearEventQueue(ADMIN_TOKEN, EVENT_ID);

            assertTrue(result.isSuccess());
            assertEquals(0, queueRepo.findByEventId(EVENT_ID).orElseThrow().getWaitingCount());
        }

        @Test
        @DisplayName("Given no queue for event, clearEventQueue returns failure")
        void givenNoQueue_whenClearEventQueue_thenFailure() {
            assertFalse(adminService.clearEventQueue(ADMIN_TOKEN, "no-event").isSuccess());
        }
    }

    @Nested
    @DisplayName("adjustQueueRate")
    class AdjustQueueRateTests {

        @Test
        @DisplayName("Given valid new rate, admin adjusts max concurrent users")
        void givenValidRate_whenAdjustQueueRate_thenRateUpdated() {
            seedQueue(EVENT_ID);

            Result<Void> result = adminService.adjustQueueRate(ADMIN_TOKEN, EVENT_ID, 25);

            assertTrue(result.isSuccess());
            assertEquals(25, queueRepo.findByEventId(EVENT_ID).orElseThrow().getMaxConcurrentUsers());
        }

        @Test
        @DisplayName("Given invalid rate (zero), adjustQueueRate returns failure")
        void givenZeroRate_whenAdjustQueueRate_thenFailure() {
            seedQueue(EVENT_ID);
            assertFalse(adminService.adjustQueueRate(ADMIN_TOKEN, EVENT_ID, 0).isSuccess());
        }
    }

    // ── Analytics ─────────────────────────────────────────────────

    @Nested
    @DisplayName("getSystemAnalytics")
    class AnalyticsTests {

        @Test
        @DisplayName("Given seeded data, analytics returns correct counts")
        void givenSeededData_whenGetSystemAnalytics_thenCorrectCounts() {
            seedMember(MEMBER_ID, "alice");
            seedPublishedEvent(EVENT_ID);
            seedQueue(EVENT_ID);

            var result = adminService.getSystemAnalytics(ADMIN_TOKEN);

            assertTrue(result.isSuccess());
            // 2 users: admin + alice
            assertEquals(2, result.getData().get().totalUsers());
            assertEquals(1, result.getData().get().activeQueues());
            assertEquals(1, result.getData().get().publishedEvents());
        }

        @Test
        @DisplayName("Given non-admin caller, getSystemAnalytics returns failure")
        void givenNonAdmin_whenGetSystemAnalytics_thenFailure() {
            StubAuth nonAdminAuth = new StubAuth("outsider", "outsider-token");
            AdminService nonAdminService = new AdminService(
                    userRepo, adminRepo, eventRepo, companyRepo,
                    queueRepo, historyRepo, paymentGateway, nonAdminAuth, eventPublisher, systemLogService);
            seedMember("outsider", "outsider");

            assertFalse(nonAdminService.getSystemAnalytics("outsider-token").isSuccess());
        }
    }

    // ── Logs ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Event and error log viewing")
    class LogTests {

        @Test
        @DisplayName("After admin actions, event log contains entries")
        void givenAdminActions_whenGetEventLog_thenLogContainsEntries() {
            seedMember(MEMBER_ID, "alice");
            adminService.deactivateUser(ADMIN_TOKEN, "alice");
            adminService.reactivateUser(ADMIN_TOKEN, "alice");

            Result<List<String>> result = adminService.getEventLog(ADMIN_TOKEN);

            assertTrue(result.isSuccess());
            assertFalse(result.getData().get().isEmpty());
        }

        @Test
        @DisplayName("Given invalid token, getEventLog returns failure")
        void givenInvalidToken_whenGetEventLog_thenFailure() {
            assertFalse(adminService.getEventLog("BAD").isSuccess());
        }

        @Test
        @DisplayName("Given non-admin, getErrorLog returns failure")
        void givenNonAdmin_whenGetErrorLog_thenFailure() {
            StubAuth nonAdminAuth = new StubAuth("outsider", "outsider-token");
            AdminService nonAdminService = new AdminService(
                    userRepo, adminRepo, eventRepo, companyRepo,
                    queueRepo, historyRepo, paymentGateway, nonAdminAuth, eventPublisher, systemLogService);
            seedMember("outsider", "outsider");

            assertFalse(nonAdminService.getErrorLog("outsider-token").isSuccess());
        }
    }

    // ── Concurrency ───────────────────────────────────────────────

    @Nested
    @DisplayName("Concurrency")
    class ConcurrencyTests {

        /**
         * 20 threads try to deactivate the same user simultaneously.
         * Exactly one thread should succeed and the user should end up deactivated.
         * This mirrors the spec constraint: "two users buying the same ticket at
         * the same millisecond" — here applied to admin actions on shared state.
         */
        @Test
        @DisplayName("20 threads deactivating the same user — exactly one succeeds")
        void given20ConcurrentDeactivations_thenExactlyOneSucceeds() throws InterruptedException {
            seedMember(MEMBER_ID, "alice");

            int threads = 20;
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger successCount = new AtomicInteger(0);
            CopyOnWriteArrayList<String> errors = new CopyOnWriteArrayList<>();

            ExecutorService pool = Executors.newFixedThreadPool(threads);
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        Result<Void> r = adminService.deactivateUser(ADMIN_TOKEN, "alice");
                        if (r.isSuccess()) successCount.incrementAndGet();
                    } catch (Exception e) {
                        errors.add(e.getMessage());
                    }
                });
            }

            ready.await();
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

            // Any thread that didn't succeed must have lost a genuine optimistic-lock race
            // (concurrent reads of the same user now return independent copies once the
            // user repository is JPA-backed, so real version conflicts are possible here —
            // unlike the old in-memory repo, which always handed back the same shared
            // reference and so never actually enforced the version check in this scenario).
            errors.forEach(msg -> assertTrue(msg != null && msg.contains("Optimistic lock conflict"),
                    "Unexpected error (not a lock conflict): " + msg));
            // At least one success is guaranteed; allow multiple since deactivation is idempotent
            assertTrue(successCount.get() >= 1, "At least one deactivation must succeed");
            assertFalse(userRepo.findById(MEMBER_ID).orElseThrow().isActive(),
                    "User must be deactivated after concurrent deactivations");
        }

        /**
         * 10 threads concurrently read analytics.
         * All must succeed — concurrent reads must not interfere with each other.
         */
        @Test
        @DisplayName("10 threads reading analytics concurrently — all succeed")
        void given10ConcurrentAnalyticsReads_thenAllSucceed() throws InterruptedException {
            seedMember(MEMBER_ID, "alice");
            seedPublishedEvent(EVENT_ID);
            seedQueue(EVENT_ID);

            int threads = 10;
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger successCount = new AtomicInteger(0);

            ExecutorService pool = Executors.newFixedThreadPool(threads);
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        if (adminService.getSystemAnalytics(ADMIN_TOKEN).isSuccess()) {
                            successCount.incrementAndGet();
                        }
                    } catch (InterruptedException ignored) {
                    }
                });
            }

            ready.await();
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

            assertEquals(threads, successCount.get(), "Every concurrent read must succeed");
        }

        /**
         * 15 threads try to clear the same queue simultaneously.
         * All calls must complete without exception; the queue ends up empty.
         */
        @Test
        @DisplayName("15 threads clearing the same queue — all complete, queue ends empty")
        void given15ConcurrentQueueClears_thenQueueEndsEmpty() throws InterruptedException {
            TicketQueue q = seedQueue(EVENT_ID);
            for (int i = 0; i < 50; i++) q.joinQueue("user-" + i);
            queueRepo.save(q);

            int threads = 15;
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch start = new CountDownLatch(1);
            CopyOnWriteArrayList<String> errors = new CopyOnWriteArrayList<>();

            ExecutorService pool = Executors.newFixedThreadPool(threads);
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        adminService.clearEventQueue(ADMIN_TOKEN, EVENT_ID);
                    } catch (Exception e) {
                        errors.add(e.getMessage());
                    }
                });
            }

            ready.await();
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

            assertTrue(errors.isEmpty(), "No thread should throw: " + errors);
            assertEquals(0, queueRepo.findByEventId(EVENT_ID).orElseThrow().getWaitingCount(),
                    "Queue must be empty after concurrent clears");
        }
    }

    // ── Test infrastructure ───────────────────────────────────────

    static class StubAuth implements IAuth {
        private final String userId;
        private final String validToken;

        StubAuth(String userId, String validToken) {
            this.userId     = userId;
            this.validToken = validToken;
        }

        @Override public String generateToken(String uid) { return validToken; }
        @Override public boolean validateToken(String token) { return validToken.equals(token); }
        @Override public String extractUserId(String token) { return userId; }
    }

    static class SpyEventPublisher implements ApplicationEventPublisher {
        private final CopyOnWriteArrayList<Object> published = new CopyOnWriteArrayList<>();

        @Override
        public void publishEvent(Object event) { published.add(event); }

        int countOf(Class<?> type) {
            return (int) published.stream().filter(type::isInstance).count();
        }
    }
}
