package com.sadna.group13a.application.Services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sadna.group13a.application.DTO.UserDTO;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.infrastructure.PasswordEncoderImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.OrderHistoryRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.UserRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.FakeUserJpaRepository;
import com.sadna.group13a.infrastructure.config.PersistenceConfig;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.FakeOrderHistoryJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class UserServiceIntegrationTest {

    private UserRepositoryImpl         userRepo;
    private OrderHistoryRepositoryImpl historyRepo;
    private PasswordEncoderImpl        passwordEncoder;
    private StubAuth                   auth;
    private UserService                userService;

    @BeforeEach
    void setUp() {
        userRepo        = new UserRepositoryImpl(new FakeUserJpaRepository());
        historyRepo     = new OrderHistoryRepositoryImpl(new FakeOrderHistoryJpaRepository(), new PersistenceConfig().domainObjectMapper());
        passwordEncoder = new PasswordEncoderImpl();
        auth            = new StubAuth();

        userService = new UserService(
                userRepo, auth, passwordEncoder, historyRepo,
                new ObjectMapper().findAndRegisterModules()
        );
    }

    // ── register ──────────────────────────────────────────────────

    @Nested
    @DisplayName("register")
    class RegisterTests {

        @Test
        @DisplayName("Success: user is stored and DTO carries correct username")
        void givenFreshUsername_whenRegister_thenUserStoredAndDtoReturned() {
            Result<UserDTO> result = userService.register("alice", "password123");

            assertTrue(result.isSuccess());
            assertEquals("alice", result.getOrThrow().username());
            assertTrue(userRepo.findByUsername("alice").isPresent());
        }

        @Test
        @DisplayName("Duplicate username returns failure without creating a second entry")
        void givenDuplicateUsername_whenRegister_thenFailure() {
            userService.register("alice", "first");
            Result<UserDTO> second = userService.register("alice", "second");

            assertFalse(second.isSuccess());
            assertEquals(1, userRepo.findAll().stream()
                    .filter(u -> u.getUsername().equals("alice")).count());
        }
    }

    // ── login ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("login")
    class LoginTests {

        @Test
        @DisplayName("Correct credentials return a non-null token")
        void givenCorrectCredentials_whenLogin_thenTokenReturned() {
            userService.register("bob", "secret");
            Result<String> result = userService.login("bob", "secret");

            assertTrue(result.isSuccess());
            assertNotNull(result.getOrThrow());
        }

        @Test
        @DisplayName("Wrong password returns failure")
        void givenWrongPassword_whenLogin_thenFailure() {
            userService.register("bob", "secret");
            assertFalse(userService.login("bob", "wrong").isSuccess());
        }

        @Test
        @DisplayName("Unknown username returns failure")
        void givenUnknownUsername_whenLogin_thenFailure() {
            assertFalse(userService.login("ghost", "anything").isSuccess());
        }
    }

    // ── getUserProfile ────────────────────────────────────────────

    @Nested
    @DisplayName("getUserProfile")
    class GetUserProfileTests {

        @Test
        @DisplayName("Valid token returns DTO with matching username")
        void givenValidToken_whenGetUserProfile_thenDtoReturned() {
            userService.register("carol", "pw");
            String token = userService.login("carol", "pw").getOrThrow();

            Result<UserDTO> result = userService.getUserProfile(token);

            assertTrue(result.isSuccess());
            assertEquals("carol", result.getOrThrow().username());
        }

        @Test
        @DisplayName("Invalid token returns failure")
        void givenInvalidToken_whenGetUserProfile_thenFailure() {
            assertFalse(userService.getUserProfile("not-a-real-token").isSuccess());
        }
    }

    // ── enterAsGuest ──────────────────────────────────────────────

    @Nested
    @DisplayName("enterAsGuest")
    class EnterAsGuestTests {

        @Test
        @DisplayName("Always succeeds and returns a non-null token")
        void whenEnterAsGuest_thenNonNullTokenReturned() {
            Result<String> result = userService.enterAsGuest();

            assertTrue(result.isSuccess());
            assertNotNull(result.getOrThrow());
        }
    }

    // ── updateProfile ─────────────────────────────────────────────

    @Nested
    @DisplayName("updateProfile")
    class UpdateProfileTests {

        @Test
        @DisplayName("Success: username is changed in the repo")
        void givenValidNewUsername_whenUpdateProfile_thenUsernameChanged() {
            userService.register("dave", "pw");
            String token = userService.login("dave", "pw").getOrThrow();

            Result<UserDTO> result = userService.updateProfile(token, "david");

            assertTrue(result.isSuccess());
            assertEquals("david", result.getOrThrow().username());
            assertTrue(userRepo.findByUsername("david").isPresent());
            assertTrue(userRepo.findByUsername("dave").isEmpty());
        }

        @Test
        @DisplayName("Already-taken username returns failure")
        void givenTakenUsername_whenUpdateProfile_thenFailure() {
            userService.register("eve", "pw");
            userService.register("frank", "pw");
            String token = userService.login("eve", "pw").getOrThrow();

            assertFalse(userService.updateProfile(token, "frank").isSuccess());
        }

        @Test
        @DisplayName("Blank username returns failure")
        void givenBlankUsername_whenUpdateProfile_thenFailure() {
            userService.register("grace", "pw");
            String token = userService.login("grace", "pw").getOrThrow();

            assertFalse(userService.updateProfile(token, "   ").isSuccess());
        }
    }

    // ── logout ────────────────────────────────────────────────────

    @Nested
    @DisplayName("logout")
    class LogoutTests {

        @Test
        @DisplayName("Valid token succeeds and returns a fresh guest token")
        void givenValidToken_whenLogout_thenSuccessAndGuestTokenReturned() {
            userService.register("hank", "pw");
            String memberToken = userService.login("hank", "pw").getOrThrow();

            Result<String> result = userService.logout(memberToken);

            assertTrue(result.isSuccess());
            String guestToken = result.getOrThrow();
            assertNotNull(guestToken);
            assertNotEquals(memberToken, guestToken);
        }

        @Test
        @DisplayName("After logout, guest token cannot view order history")
        void givenLoggedOutMember_whenViewOrderHistory_thenFailsWithMembersOnlyMessage() {
            userService.register("ivan", "pw");
            String memberToken = userService.login("ivan", "pw").getOrThrow();
            String guestToken = userService.logout(memberToken).getOrThrow();

            Result<?> result = userService.viewOrderHistory(guestToken);

            assertFalse(result.isSuccess());
            assertTrue(result.getErrorMessage().contains("registered members"));
        }

        @Test
        @DisplayName("After logout, guest token cannot update profile")
        void givenLoggedOutMember_whenUpdateProfile_thenFails() {
            userService.register("judy", "pw");
            String memberToken = userService.login("judy", "pw").getOrThrow();
            String guestToken = userService.logout(memberToken).getOrThrow();

            Result<?> result = userService.updateProfile(guestToken, "newname");

            assertFalse(result.isSuccess());
            assertTrue(userRepo.findByUsername("judy").isPresent(), "Original member record must be untouched");
        }
    }

    // ── Concurrency ───────────────────────────────────────────────

    @Nested
    @DisplayName("Concurrency")
    class ConcurrencyTests {

        // register() has a check-then-act gap; with 30 threads racing on the same
        // username exactly one should land and the rest should get a clean failure.
        @Test
        @DisplayName("30 threads registering same username — exactly 1 succeeds, user stored once")
        void given30ConcurrentRegistrations_thenExactlyOneSucceeds() throws InterruptedException {
            int threads = 30;
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger successCount = new AtomicInteger(0);
            CopyOnWriteArrayList<String> uncaughtErrors = new CopyOnWriteArrayList<>();

            ExecutorService pool = Executors.newFixedThreadPool(threads);
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        Result<UserDTO> r = userService.register("raceUser", "pw");
                        if (r.isSuccess()) successCount.incrementAndGet();
                    } catch (Exception e) {
                        uncaughtErrors.add(e.getMessage());
                    }
                });
            }

            ready.await();
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS));

            assertTrue(uncaughtErrors.isEmpty(), "No thread should throw: " + uncaughtErrors);
            assertEquals(1, successCount.get(), "Exactly one registration must succeed");
            assertEquals(1,
                    userRepo.findAll().stream()
                            .filter(u -> u.getUsername().equals("raceUser")).count(),
                    "raceUser must appear exactly once in the repo");
        }
    }

    // ── Test infrastructure ───────────────────────────────────────

    // generateToken called by login() and enterAsGuest() — must register the token
    // so later validateToken/extractUserId calls work.
    static class StubAuth implements IAuth {
        private final ConcurrentHashMap<String, String> tokenToUser = new ConcurrentHashMap<>();

        @Override
        public String generateToken(String userId) {
            String token = "token-" + userId;
            tokenToUser.put(token, userId);
            return token;
        }

        @Override
        public boolean validateToken(String token) {
            return token != null && tokenToUser.containsKey(token);
        }

        @Override
        public String extractUserId(String token) {
            return tokenToUser.get(token);
        }
    }
}
