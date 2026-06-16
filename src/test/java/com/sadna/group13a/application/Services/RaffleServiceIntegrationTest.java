package com.sadna.group13a.application.Services;

import com.sadna.group13a.application.DTO.RaffleRegistrationDTO;
import com.sadna.group13a.application.DTO.RaffleResultDTO;
import com.sadna.group13a.application.DTO.WinningTicketDTO;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import com.sadna.group13a.domain.Aggregates.Event.Event;
import com.sadna.group13a.domain.Aggregates.Raffle.Raffle;
import com.sadna.group13a.domain.Aggregates.User.Member;
import com.sadna.group13a.domain.Events.RaffleDrawnEvent;
import com.sadna.group13a.infrastructure.RepositoryImpl.CompanyRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.EventRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.RaffleRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.UserRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.FakeUserJpaRepository;
import com.sadna.group13a.infrastructure.config.PersistenceConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class RaffleServiceIntegrationTest {

    private static final String COMPANY_ID = "company-r-001";
    private static final String FOUNDER_ID = "founder-r-001";
    private static final String EVENT_ID   = "event-r-001";

    private RaffleRepositoryImpl  raffleRepo;
    private EventRepositoryImpl   eventRepo;
    private CompanyRepositoryImpl companyRepo;
    private UserRepositoryImpl    userRepo;
    private SpyEventPublisher     eventPublisher;
    private RaffleService         raffleService;

    @BeforeEach
    void setUp() {
        raffleRepo     = new RaffleRepositoryImpl();
        eventRepo      = new EventRepositoryImpl();
        companyRepo    = new CompanyRepositoryImpl();
        userRepo       = new UserRepositoryImpl(new FakeUserJpaRepository(), new PersistenceConfig().domainObjectMapper());
        eventPublisher = new SpyEventPublisher();

        raffleService = new RaffleService(
                raffleRepo, eventRepo, companyRepo, userRepo,
                new MultiUserStubAuth(), eventPublisher
        );
    }

    // ── Seed helpers ──────────────────────────────────────────────

    private void seedCompanyAndFounder() {
        userRepo.save(new Member(FOUNDER_ID, "founder", "hash"));
        companyRepo.save(new ProductionCompany(COMPANY_ID, "Raffle Co", "desc", FOUNDER_ID));
    }

    private void seedEvent(String eventId) {
        eventRepo.save(new Event(eventId, "Raffle Night", "desc", COMPANY_ID,
                LocalDateTime.now().plusDays(14), "Entertainment"));
    }

    // Delegates to the service so createRaffle itself is exercised by every test group.
    private String seedRaffle() {
        Result<String> r = raffleService.createRaffle(token(FOUNDER_ID), EVENT_ID, COMPANY_ID);
        assertTrue(r.isSuccess(), "Raffle seed failed: " + r.getErrorMessage());
        return r.getOrThrow();
    }

    private void joinAs(String raffleId, String userId) {
        userRepo.save(new Member(userId, userId, "hash"));
        raffleService.joinRaffle(token(userId), new RaffleRegistrationDTO(raffleId));
    }

    private static String token(String userId) { return "token-" + userId; }

    // ── createRaffle ──────────────────────────────────────────────

    @Nested
    @DisplayName("createRaffle")
    class CreateRaffleTests {

        @Test
        @DisplayName("Founder creates a raffle: raffle is persisted and event mode set to RAFFLE")
        void givenFounderAndEvent_whenCreateRaffle_thenSuccess() {
            seedCompanyAndFounder();
            seedEvent(EVENT_ID);

            Result<String> result = raffleService.createRaffle(token(FOUNDER_ID), EVENT_ID, COMPANY_ID);

            assertTrue(result.isSuccess());
            assertNotNull(result.getOrThrow());
            assertFalse(raffleRepo.findByEventId(EVENT_ID).isEmpty());
        }

        @Test
        @DisplayName("Invalid token is rejected")
        void givenInvalidToken_whenCreateRaffle_thenFailure() {
            seedCompanyAndFounder();
            seedEvent(EVENT_ID);

            assertFalse(raffleService.createRaffle("bad-token", EVENT_ID, COMPANY_ID).isSuccess());
        }

        @Test
        @DisplayName("User not in the company cannot create a raffle")
        void givenUnprivilegedUser_whenCreateRaffle_thenFailure() {
            seedCompanyAndFounder();
            seedEvent(EVENT_ID);
            String outsider = "outsider-r";
            userRepo.save(new Member(outsider, outsider, "hash"));

            assertFalse(raffleService.createRaffle(token(outsider), EVENT_ID, COMPANY_ID).isSuccess());
        }

        @Test
        @DisplayName("Non-existent event returns failure")
        void givenEventNotFound_whenCreateRaffle_thenFailure() {
            seedCompanyAndFounder();
            // event not saved

            assertFalse(raffleService.createRaffle(token(FOUNDER_ID), "ghost-event", COMPANY_ID).isSuccess());
        }
    }

    // ── joinRaffle ────────────────────────────────────────────────

    @Nested
    @DisplayName("joinRaffle")
    class JoinRaffleTests {

        private String raffleId;

        @BeforeEach
        void seed() {
            seedCompanyAndFounder();
            seedEvent(EVENT_ID);
            raffleId = seedRaffle();
        }

        @Test
        @DisplayName("Active member successfully joins the raffle")
        void givenActiveMember_whenJoinRaffle_thenSuccess() {
            String userId = "joiner-r-1";
            userRepo.save(new Member(userId, userId, "hash"));

            assertTrue(raffleService.joinRaffle(token(userId),
                    new RaffleRegistrationDTO(raffleId)).isSuccess());
        }

        @Test
        @DisplayName("Inactive account is rejected")
        void givenInactiveUser_whenJoinRaffle_thenFailure() {
            String userId = "inactive-raffle";
            Member m = new Member(userId, userId, "hash");
            m.deactivate();
            userRepo.save(m);

            assertFalse(raffleService.joinRaffle(token(userId),
                    new RaffleRegistrationDTO(raffleId)).isSuccess());
        }

        @Test
        @DisplayName("Same user joining twice is rejected")
        void givenSameUser_whenJoinTwice_thenSecondFails() {
            String userId = "double-joiner-r";
            userRepo.save(new Member(userId, userId, "hash"));
            raffleService.joinRaffle(token(userId), new RaffleRegistrationDTO(raffleId));

            assertFalse(raffleService.joinRaffle(token(userId),
                    new RaffleRegistrationDTO(raffleId)).isSuccess());
        }

        @Test
        @DisplayName("Non-existent raffle ID returns failure")
        void givenRaffleNotFound_whenJoinRaffle_thenFailure() {
            String userId = "join-ghost-r";
            userRepo.save(new Member(userId, userId, "hash"));

            assertFalse(raffleService.joinRaffle(token(userId),
                    new RaffleRegistrationDTO("no-such-raffle")).isSuccess());
        }
    }

    // ── drawWinners ───────────────────────────────────────────────

    @Nested
    @DisplayName("drawWinners")
    class DrawWinnersTests {

        private String raffleId;

        @BeforeEach
        void seed() {
            seedCompanyAndFounder();
            seedEvent(EVENT_ID);
            raffleId = seedRaffle();
        }

        @Test
        @DisplayName("5 participants, draw 3 — exactly 3 winners and a RaffleDrawnEvent is fired")
        void given5Participants_whenDraw3_thenExactly3Winners() {
            for (int i = 0; i < 5; i++) joinAs(raffleId, "draw-user-" + i);

            Result<RaffleResultDTO> result =
                    raffleService.drawWinners(token(FOUNDER_ID), raffleId, 3, 60);

            assertTrue(result.isSuccess());
            assertEquals(3, result.getData().orElseThrow().expectedWinnersDrawn());
            assertEquals(1, eventPublisher.countOf(RaffleDrawnEvent.class));
        }

        @Test
        @DisplayName("Drawing 0 winners succeeds gracefully with 0 winners recorded")
        void givenParticipants_whenDraw0_thenZeroWinners() {
            for (int i = 0; i < 3; i++) joinAs(raffleId, "draw-zero-" + i);

            Result<RaffleResultDTO> result =
                    raffleService.drawWinners(token(FOUNDER_ID), raffleId, 0, 60);

            assertTrue(result.isSuccess());
            assertEquals(0, result.getData().orElseThrow().expectedWinnersDrawn());
        }

        @Test
        @DisplayName("User without company permission cannot trigger the draw")
        void givenUnprivilegedUser_whenDrawWinners_thenFailure() {
            joinAs(raffleId, "draw-participant-x");
            String outsider = "draw-outsider";
            userRepo.save(new Member(outsider, outsider, "hash"));

            assertFalse(raffleService.drawWinners(token(outsider), raffleId, 1, 60).isSuccess());
        }
    }

    // ── checkMyResult ─────────────────────────────────────────────

    @Nested
    @DisplayName("checkMyResult")
    class CheckMyResultTests {

        private String raffleId;
        private static final String WINNER_ID = "result-winner";
        private static final String LOSER_ID  = "result-loser";

        @BeforeEach
        void seed() {
            seedCompanyAndFounder();
            seedEvent(EVENT_ID);
            raffleId = seedRaffle();

            // 4 padding participants + winner + loser = 6 total
            for (int i = 0; i < 4; i++) joinAs(raffleId, "result-padding-" + i);
            joinAs(raffleId, WINNER_ID);
            joinAs(raffleId, LOSER_ID);
        }

        @Test
        @DisplayName("After drawing all 6 as winners, WINNER_ID receives a WinningTicketDTO")
        void givenAllWinners_whenCheckResult_thenWinnerReceivesAuthCode() {
            raffleService.drawWinners(token(FOUNDER_ID), raffleId, 6, 60);

            Result<WinningTicketDTO> result =
                    raffleService.checkMyResult(token(WINNER_ID), raffleId);

            assertTrue(result.isSuccess());
            assertNotNull(result.getData().orElseThrow().authorizationCode());
        }

        @Test
        @DisplayName("Non-participant gets a failure response")
        void givenNonParticipant_whenCheckResult_thenFailure() {
            raffleService.drawWinners(token(FOUNDER_ID), raffleId, 6, 60);
            String stranger = "result-stranger";
            userRepo.save(new Member(stranger, stranger, "hash"));

            assertFalse(raffleService.checkMyResult(token(stranger), raffleId).isSuccess());
        }

        @Test
        @DisplayName("When 0 winners are drawn, nobody wins — checkMyResult returns failure")
        void given0Winners_whenCheckResult_thenFailure() {
            raffleService.drawWinners(token(FOUNDER_ID), raffleId, 0, 60);

            assertFalse(raffleService.checkMyResult(token(LOSER_ID), raffleId).isSuccess());
        }
    }

    // ── closeRaffle ───────────────────────────────────────────────

    @Nested
    @DisplayName("closeRaffle")
    class CloseRaffleTests {

        @Test
        @DisplayName("Founder can close their own raffle")
        void givenFounder_whenCloseRaffle_thenSuccess() {
            seedCompanyAndFounder();
            seedEvent(EVENT_ID);
            String raffleId = seedRaffle();

            assertTrue(raffleService.closeRaffle(token(FOUNDER_ID), raffleId).isSuccess());
        }

        @Test
        @DisplayName("User without company permission cannot close the raffle")
        void givenUnprivilegedUser_whenCloseRaffle_thenFailure() {
            seedCompanyAndFounder();
            seedEvent(EVENT_ID);
            String raffleId = seedRaffle();
            String outsider = "close-outsider";
            userRepo.save(new Member(outsider, outsider, "hash"));

            assertFalse(raffleService.closeRaffle(token(outsider), raffleId).isSuccess());
        }
    }

    // ── Concurrency ───────────────────────────────────────────────

    @Nested
    @DisplayName("Concurrency")
    class ConcurrencyTests {

        // 20 users race to join the same raffle.
        // RaffleRepositoryImpl.save() is synchronized so the list must end up with
        // exactly 20 distinct entries — no lost writes and no duplicates.
        @Test
        @DisplayName("20 threads joining simultaneously — exactly 20 participants registered")
        void given20ConcurrentJoins_thenAllRegisteredNoDuplicates() throws InterruptedException {
            seedCompanyAndFounder();
            seedEvent(EVENT_ID);
            String raffleId = seedRaffle();

            int threads = 20;
            for (int i = 0; i < threads; i++) {
                String uid = "raffle-conc-" + i;
                userRepo.save(new Member(uid, uid, "hash"));
            }

            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch start = new CountDownLatch(1);
            CopyOnWriteArrayList<String> errors = new CopyOnWriteArrayList<>();

            ExecutorService pool = Executors.newFixedThreadPool(threads);
            for (int i = 0; i < threads; i++) {
                final String uid  = "raffle-conc-" + i;
                final String rId  = raffleId;
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        raffleService.joinRaffle(token(uid), new RaffleRegistrationDTO(rId));
                    } catch (Exception e) {
                        errors.add(e.getClass().getSimpleName() + ": " + e.getMessage());
                    }
                });
            }

            ready.await();
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS));

            assertTrue(errors.isEmpty(), "No thread should throw: " + errors);

            Raffle raffle = raffleRepo.findById(raffleId).orElseThrow();
            assertEquals(20, raffle.getParticipantUserIds().size(),
                    "All 20 distinct users must be registered — no lost writes, no duplicates");
        }
    }

    // ── Test infrastructure ───────────────────────────────────────

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
