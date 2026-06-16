package com.sadna.group13a.acceptance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sadna.group13a.application.Services.CompanyService;
import com.sadna.group13a.application.Services.UserService;
import com.sadna.group13a.domain.DomainServices.CompanyStaffDomainService;
import com.sadna.group13a.infrastructure.AuthImpl;
import com.sadna.group13a.infrastructure.PasswordEncoderImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.CompanyRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.OrderHistoryRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.UserRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.FakeCompanyJpaRepository;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.FakeOrderHistoryJpaRepository;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.FakeUserJpaRepository;
import com.sadna.group13a.infrastructure.config.PersistenceConfig;
import com.sadna.group13a.infrastructure.init.InitialStateLoader;
import com.sadna.group13a.infrastructure.init.InitialStateRunner;
import com.sadna.group13a.infrastructure.init.SystemConfig;
import com.sadna.group13a.infrastructure.init.SystemConfigValidator;
import com.sadna.group13a.infrastructure.init.SystemInitializationException;
import com.sadna.group13a.infrastructure.init.SystemStartupConfigValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UC — System initialization (issue #230).
 *
 * Verifies the system starts with a valid configuration / initial-state file and refuses
 * to start (clear, catchable failure) when either is invalid. Uses dedicated in-memory
 * test services and temp files — no production DB or external systems.
 */
@DisplayName("Issue #230 — Valid & invalid system initialization")
class SystemInitializationTest {

    private UserRepositoryImpl userRepo;
    private CompanyRepositoryImpl companyRepo;
    private InitialStateLoader loader;

    @BeforeEach
    void setUp() {
        userRepo = new UserRepositoryImpl(new FakeUserJpaRepository(), new PersistenceConfig().domainObjectMapper());
        companyRepo = new CompanyRepositoryImpl(new FakeCompanyJpaRepository(), new PersistenceConfig().domainObjectMapper());
        OrderHistoryRepositoryImpl historyRepo = new OrderHistoryRepositoryImpl(new FakeOrderHistoryJpaRepository(), new PersistenceConfig().domainObjectMapper());
        AuthImpl auth = new AuthImpl();

        UserService userService = new UserService(
                userRepo, auth, new PasswordEncoderImpl(), historyRepo,
                new ObjectMapper().findAndRegisterModules());
        CompanyService companyService = new CompanyService(
                companyRepo, userRepo, historyRepo, auth, new ObjectMapper(),
                event -> { /* no-op publisher */ }, new CompanyStaffDomainService());

        loader = new InitialStateLoader(userService, companyService);
    }

    // ── Configuration file validation ─────────────────────────────

    @Nested
    @DisplayName("Configuration file")
    class ConfigValidationTests {

        @Test
        @DisplayName("Valid config → system starts successfully")
        void validConfig_passes() {
            assertDoesNotThrow(() -> SystemConfigValidator.validate(
                    new SystemConfig("admin", "admin123", "")));
        }

        @Test
        @DisplayName("Missing required field (admin username) → refuses to start")
        void missingUsername_fails() {
            SystemInitializationException ex = assertThrows(SystemInitializationException.class,
                    () -> SystemConfigValidator.validate(new SystemConfig("", "admin123", "")));
            assertTrue(ex.getMessage().contains("username"));
        }

        @Test
        @DisplayName("Missing required field (admin password) → refuses to start")
        void missingPassword_fails() {
            assertThrows(SystemInitializationException.class,
                    () -> SystemConfigValidator.validate(new SystemConfig("admin", "  ", "")));
        }

        @Test
        @DisplayName("Invalid field value (password too short) → refuses to start")
        void invalidPasswordValue_fails() {
            assertThrows(SystemInitializationException.class,
                    () -> SystemConfigValidator.validate(new SystemConfig("admin", "ab", "")));
        }

        @Test
        @DisplayName("Null config → refuses to start")
        void nullConfig_fails() {
            assertThrows(SystemInitializationException.class, () -> SystemConfigValidator.validate(null));
        }

        @Test
        @DisplayName("Startup validator bean: valid config constructs, invalid throws")
        void startupValidatorBean() {
            assertDoesNotThrow(() -> new SystemStartupConfigValidator("admin", "admin123", ""));
            assertThrows(SystemInitializationException.class,
                    () -> new SystemStartupConfigValidator("", "admin123", ""));
        }
    }

    // ── Initial-state file execution ──────────────────────────────

    @Nested
    @DisplayName("Initial-state file")
    class InitialStateFileTests {

        @Test
        @DisplayName("Valid sequence of legal operations → system reaches expected state")
        void validSequence_reachesExpectedState() {
            loader.execute(List.of(
                    "# seed an owner and a company",
                    "register alice secret1",
                    "login alice secret1",
                    "create-company alice \"SoundWave Entertainment\" \"Live music\""));

            assertTrue(userRepo.findByUsername("alice").isPresent(), "user must be registered");
            assertEquals(1, companyRepo.findAll().size(), "company must be created");
            assertEquals("SoundWave Entertainment", companyRepo.findAll().get(0).getName());
        }

        @Test
        @DisplayName("One operation in the sequence fails → entire initialization fails with an error")
        void failingOperation_abortsInitialization() {
            SystemInitializationException ex = assertThrows(SystemInitializationException.class,
                    () -> loader.execute(List.of(
                            "register alice secret1",
                            "login alice WRONG_PASSWORD")));
            assertTrue(ex.getMessage().toLowerCase().contains("login"));
        }

        @Test
        @DisplayName("Illegal operation (create company without login) → initialization fails")
        void illegalOperation_fails() {
            SystemInitializationException ex = assertThrows(SystemInitializationException.class,
                    () -> loader.execute(List.of(
                            "register bob secret1",
                            "create-company bob \"NoLoginCo\"")));
            assertTrue(ex.getMessage().toLowerCase().contains("logged in"));
        }

        @Test
        @DisplayName("Logout then act → illegal operation fails")
        void actionAfterLogout_fails() {
            assertThrows(SystemInitializationException.class,
                    () -> loader.execute(List.of(
                            "register carol secret1",
                            "login carol secret1",
                            "logout carol",
                            "create-company carol \"AfterLogoutCo\"")));
        }

        @Test
        @DisplayName("Empty file → system starts with a clean state (no error)")
        void emptyFile_cleanStart() {
            assertDoesNotThrow(() -> loader.execute(List.of()));
            assertDoesNotThrow(() -> loader.execute(List.of("", "   ", "# only comments")));
            assertTrue(userRepo.findAll().isEmpty());
            assertTrue(companyRepo.findAll().isEmpty());
        }

        @Test
        @DisplayName("Malformed syntax (unknown command) → parse error")
        void malformedUnknownCommand_fails() {
            SystemInitializationException ex = assertThrows(SystemInitializationException.class,
                    () -> loader.execute(List.of("frobnicate alice")));
            assertTrue(ex.getMessage().toLowerCase().contains("parse error"));
        }

        @Test
        @DisplayName("Malformed syntax (unterminated quote) → parse error")
        void malformedUnterminatedQuote_fails() {
            assertThrows(SystemInitializationException.class,
                    () -> loader.execute(List.of("register \"alice secret1")));
        }

        @Test
        @DisplayName("Malformed syntax (wrong number of arguments) → parse error")
        void malformedArity_fails() {
            assertThrows(SystemInitializationException.class,
                    () -> loader.execute(List.of("register alice")));
        }
    }

    // ── File loading / start-up runner ────────────────────────────

    @Nested
    @DisplayName("File loading & runner")
    class FileLoadingTests {

        @Test
        @DisplayName("Blank path → clean start (no-op), including via the start-up runner")
        void blankPath_isNoOp() {
            assertDoesNotThrow(() -> loader.runFromFile(""));
            assertDoesNotThrow(() -> loader.runFromFile(null));
            assertDoesNotThrow(() -> new InitialStateRunner(loader, "").run(null));
        }

        @Test
        @DisplayName("Valid file on disk → executed and state reached")
        void validFileOnDisk_executed(@TempDir Path dir) throws Exception {
            Path file = dir.resolve("init.txt");
            Files.writeString(file, String.join("\n",
                    "register dave secret1",
                    "login dave secret1",
                    "create-company dave \"Dave Co\""));

            loader.runFromFile(file.toString());

            assertTrue(userRepo.findByUsername("dave").isPresent());
            assertEquals(1, companyRepo.findAll().size());
        }

        @Test
        @DisplayName("Missing file → initialization fails with a clear error")
        void missingFile_fails() {
            assertThrows(SystemInitializationException.class,
                    () -> loader.runFromFile("/path/that/definitely/does/not/exist-230.txt"));
        }
    }
}
