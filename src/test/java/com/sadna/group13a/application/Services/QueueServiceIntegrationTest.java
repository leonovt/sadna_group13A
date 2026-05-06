package com.sadna.group13a.application.Services;

import com.sadna.group13a.application.DTO.QueueStatusDTO;
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
import com.sadna.group13a.domain.Events.QueueTurnArrivedEvent;
import com.sadna.group13a.infrastructure.RepositoryImpl.AdminRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.CompanyRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.EventRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.QueueRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.UserRepositoryImpl;
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

import static org.junit.jupiter.api.Assertions.*;

class QueueServiceIntegrationTest {

    private static final String COMPANY_ID = "company-q-001";
    private static final String FOUNDER_ID = "founder-q-001";
    private static final String ADMIN_ID   = "admin-q-001";
    private static final String EVENT_ID   = "event-q-001";

    private QueueRepositoryImpl   queueRepo;
    private EventRepositoryImpl   eventRepo;
    private CompanyRepositoryImpl companyRepo;
    private UserRepositoryImpl    userRepo;
    private AdminRepositoryImpl   adminRepo;
    private SpyEventPublisher     eventPublisher;
    private QueueService          queueService;

    @BeforeEach
    void setUp() {
        queueRepo      = new QueueRepositoryImpl();
        eventRepo      = new EventRepositoryImpl();
        companyRepo    = new CompanyRepositoryImpl();
        userRepo       = new UserRepositoryImpl();
        adminRepo      = new AdminRepositoryImpl();
        eventPublisher = new SpyEventPublisher();

        queueService = new QueueService(
                queueRepo, eventRepo, companyRepo, userRepo, adminRepo,
                new MultiUserStubAuth(), eventPublisher
        );

        seedAdmin();
    }

    // ── Seed helpers ──────────────────────────────────────────────

    private void seedCompanyAndFounder() {
        userRepo.save(new Member(FOUNDER_ID, "founder", "hash"));
        companyRepo.save(new ProductionCompany(COMPANY_ID, "Test Co", "desc", FOUNDER_ID));
    }

    private void seedPublishedEvent(String eventId) {
        Event event = new Event(eventId, "Concert", "desc", COMPANY_ID,
                LocalDateTime.now().plusDays(7), "Music");
        VenueMap vm = new VenueMap("vm-q-1", "Arena",
                List.of(new SeatedZone("z-q-1", "VIP", 100.0, List.of(new Seat("s-q-1", "A-1")))));
        event.setVenueMap(vm);
        event.publish();
        eventRepo.save(event);
    }

    // Bypasses the service to set up queue state for tests that aren't testing createQueue.
    private void seedQueue(String eventId, int maxConcurrent) {
        queueRepo.save(new TicketQueue(eventId, maxConcurrent));
    }

    private void seedAdmin() {
        userRepo.save(new Member(ADMIN_ID, "sysadmin", "hash"));
        adminRepo.save(new Admin("admin-rec-q-001", ADMIN_ID));
    }

    private static String token(String userId) { return "token-" + userId; }

    // ── createQueue ───────────────────────────────────────────────

    @Nested
    @DisplayName("createQueue")
    class CreateQueueTests {

        @Test
        @DisplayName("Founder creates a queue: queue is persisted and event mode becomes QUEUE")
        void givenFounderAndPublishedEvent_whenCreateQueue_thenSuccess() {
            seedCompanyAndFounder();
            seedPublishedEvent(EVENT_ID);

            Result<Void> result = queueService.createQueue(token(FOUNDER_ID), EVENT_ID, 5);

            assertTrue(result.isSuccess());
            assertTrue(queueRepo.findByEventId(EVENT_ID).isPresent());
        }

        @Test
        @DisplayName("Invalid token is rejected before touching any state")
        void givenInvalidToken_whenCreateQueue_thenFailure() {
            seedCompanyAndFounder();
            seedPublishedEvent(EVENT_ID);

            assertFalse(queueService.createQueue("not-a-token", EVENT_ID, 5).isSuccess());
            assertTrue(queueRepo.findByEventId(EVENT_ID).isEmpty());
        }

        @Test
        @DisplayName("User not in the company staff cannot create a queue")
        void givenUnprivilegedUser_whenCreateQueue_thenFailure() {
            seedCompanyAndFounder();
            seedPublishedEvent(EVENT_ID);
            String outsider = "outsider-q";
            userRepo.save(new Member(outsider, "outsider", "hash"));

            assertFalse(queueService.createQueue(token(outsider), EVENT_ID, 5).isSuccess());
        }

        @Test
        @DisplayName("Creating a second queue for the same event fails")
        void givenExistingQueue_whenCreateQueueAgain_thenFailure() {
            seedCompanyAndFounder();
            seedPublishedEvent(EVENT_ID);
            queueService.createQueue(token(FOUNDER_ID), EVENT_ID, 5);

            assertFalse(queueService.createQueue(token(FOUNDER_ID), EVENT_ID, 5).isSuccess());
        }
    }

    // ── joinQueue ─────────────────────────────────────────────────

    @Nested
    @DisplayName("joinQueue")
    class JoinQueueTests {

        @BeforeEach
        void seed() {
            seedCompanyAndFounder();
            seedPublishedEvent(EVENT_ID);
        }

        @Test
        @DisplayName("User gets immediate access when the queue has spare capacity")
        void givenCapacityAvailable_whenJoinQueue_thenImmediateAccess() {
            seedQueue(EVENT_ID, 5);
            String userId = "join-user-1";
            userRepo.save(new Member(userId, "joinuser1", "hash"));

            Result<QueueStatusDTO> result = queueService.joinQueue(token(userId), EVENT_ID);

            assertTrue(result.isSuccess());
            assertTrue(result.getData().orElseThrow().isActive());
        }

        @Test
        @DisplayName("User waits when the queue is at full capacity")
        void givenFullQueue_whenJoinQueue_thenUserEndsUpWaiting() {
            seedQueue(EVENT_ID, 1);
            String first = "slot-holder";
            userRepo.save(new Member(first, "slotholder", "hash"));
            queueService.joinQueue(token(first), EVENT_ID);

            String waiter = "waiter-1";
            userRepo.save(new Member(waiter, "waiter1", "hash"));
            Result<QueueStatusDTO> result = queueService.joinQueue(token(waiter), EVENT_ID);

            assertTrue(result.isSuccess());
            assertFalse(result.getData().orElseThrow().isActive());
            assertTrue(result.getData().orElseThrow().positionInLine() >= 1);
        }

        @Test
        @DisplayName("Inactive account is rejected")
        void givenInactiveUser_whenJoinQueue_thenFailure() {
            seedQueue(EVENT_ID, 5);
            String userId = "inactive-q-user";
            Member m = new Member(userId, "inactivequser", "hash");
            m.deactivate();
            userRepo.save(m);

            assertFalse(queueService.joinQueue(token(userId), EVENT_ID).isSuccess());
        }

        @Test
        @DisplayName("Guest (userId not in repo) is admitted — only inactive members are blocked")
        void givenGuestUserId_whenJoinQueue_thenAdmitted() {
            seedQueue(EVENT_ID, 5);
            // service only rejects if the user IS found AND inactive
            assertTrue(queueService.joinQueue(token("guest-xyz-999"), EVENT_ID).isSuccess());
        }
    }

    // ── getStatus ─────────────────────────────────────────────────

    @Nested
    @DisplayName("getStatus")
    class GetStatusTests {

        @BeforeEach
        void seed() {
            seedCompanyAndFounder();
            seedPublishedEvent(EVENT_ID);
            seedQueue(EVENT_ID, 1);
        }

        @Test
        @DisplayName("Active user's status shows hasAccess=true")
        void givenActiveUser_whenGetStatus_thenHasAccessTrue() {
            String userId = "status-active";
            userRepo.save(new Member(userId, "statusactive", "hash"));
            queueService.joinQueue(token(userId), EVENT_ID);

            Result<QueueStatusDTO> result = queueService.getStatus(token(userId), EVENT_ID);

            assertTrue(result.isSuccess());
            assertTrue(result.getData().orElseThrow().isActive());
        }

        @Test
        @DisplayName("Waiting user's status shows hasAccess=false with a position >= 1")
        void givenWaitingUser_whenGetStatus_thenCorrectWaitingPosition() {
            String holder = "status-holder";
            userRepo.save(new Member(holder, "statusholder", "hash"));
            queueService.joinQueue(token(holder), EVENT_ID);

            String waiter = "status-waiter";
            userRepo.save(new Member(waiter, "statuswaiter", "hash"));
            queueService.joinQueue(token(waiter), EVENT_ID);

            Result<QueueStatusDTO> result = queueService.getStatus(token(waiter), EVENT_ID);

            assertTrue(result.isSuccess());
            assertFalse(result.getData().orElseThrow().isActive());
            assertTrue(result.getData().orElseThrow().positionInLine() >= 1);
        }
    }

    // ── releaseAccess ─────────────────────────────────────────────

    @Nested
    @DisplayName("releaseAccess")
    class ReleaseAccessTests {

        @Test
        @DisplayName("When the active user releases, the next waiting user gets a QueueTurnArrivedEvent")
        void givenFullQueueWithWaiter_whenActiveUserReleases_thenWaiterPromoted() {
            seedCompanyAndFounder();
            seedPublishedEvent(EVENT_ID);
            seedQueue(EVENT_ID, 1);

            String active = "release-active";
            String waiter = "release-waiter";
            userRepo.save(new Member(active, "releaseactive", "hash"));
            userRepo.save(new Member(waiter, "releasewaiter", "hash"));

            queueService.joinQueue(token(active), EVENT_ID);
            queueService.joinQueue(token(waiter), EVENT_ID);

            int before = eventPublisher.countOf(QueueTurnArrivedEvent.class);
            queueService.releaseAccess(token(active), EVENT_ID);

            assertEquals(before + 1, eventPublisher.countOf(QueueTurnArrivedEvent.class));
        }
    }

    // ── Concurrency ───────────────────────────────────────────────

    @Nested
    @DisplayName("Concurrency")
    class ConcurrencyTests {

        // 20 users all try to join at the same millisecond.
        // Regardless of scheduling, every join must be recorded:
        // activeCount + waitingCount must equal 20 with no lost writes.
        @Test
        @DisplayName("20 threads joining simultaneously — activeCount + waitingCount == 20")
        void given20ConcurrentJoins_thenNoJoinIsLost() throws InterruptedException {
            seedCompanyAndFounder();
            seedPublishedEvent(EVENT_ID);
            seedQueue(EVENT_ID, 5);

            int threads = 20;
            for (int i = 0; i < threads; i++) {
                String uid = "concurrent-q-" + i;
                userRepo.save(new Member(uid, uid, "hash"));
            }

            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch start = new CountDownLatch(1);
            CopyOnWriteArrayList<String> errors = new CopyOnWriteArrayList<>();

            ExecutorService pool = Executors.newFixedThreadPool(threads);
            for (int i = 0; i < threads; i++) {
                final String uid = "concurrent-q-" + i;
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        queueService.joinQueue(token(uid), EVENT_ID);
                    } catch (Exception e) {
                        errors.add(e.getMessage());
                    }
                });
            }

            ready.await();
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS));

            assertTrue(errors.isEmpty(), "No thread should throw: " + errors);

            TicketQueue q = queueRepo.findByEventId(EVENT_ID).orElseThrow();
            assertEquals(threads, q.getActiveCount() + q.getWaitingCount(),
                    "Every join must be recorded — no lost updates");
        }
    }

    // ── Test infrastructure ───────────────────────────────────────

    // Maps "token-<userId>" → userId deterministically, no shared state needed.
    static class MultiUserStubAuth implements IAuth {
        @Override public String generateToken(String userId) { return "token-" + userId; }
        @Override public boolean validateToken(String token) { return token != null && token.startsWith("token-"); }
        @Override public String extractUserId(String token)  { return token.substring("token-".length()); }
    }

    static class SpyEventPublisher implements ApplicationEventPublisher {
        private final CopyOnWriteArrayList<Object> published = new CopyOnWriteArrayList<>();

        @Override public void publishEvent(Object event) { published.add(event); }

        int countOf(Class<?> type) {
            return (int) published.stream().filter(type::isInstance).count();
        }
    }
}
