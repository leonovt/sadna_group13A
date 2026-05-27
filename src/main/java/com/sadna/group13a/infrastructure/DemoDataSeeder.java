package com.sadna.group13a.infrastructure;

import com.sadna.group13a.application.DTO.ZoneCreationDTO;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.CompanyService;
import com.sadna.group13a.application.Services.EventService;
import com.sadna.group13a.application.Services.RaffleService;
import com.sadna.group13a.application.Services.UserService;
import com.sadna.group13a.domain.Aggregates.Company.CompanyPermission;
import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import com.sadna.group13a.domain.Aggregates.Event.EventSaleMode;
import com.sadna.group13a.domain.Aggregates.Event.ZoneType;
import com.sadna.group13a.domain.Interfaces.ICompanyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Seeds rich demo data when the app is started with --spring.profiles.active=demo.
 * Run via: mvn spring-boot:run -Dspring-boot.run.profiles=demo
 *
 * Seeded accounts (all password: pass123):
 *   alice  — founder of SoundWave Entertainment
 *   bob    — manager at SoundWave (MANAGE_EVENTS, VIEW_REPORTS)
 *   carol  — manager at SoundWave (MANAGE_POLICIES, MANAGE_DISCOUNTS)
 *   frank  — founder of Stellar Experiences
 *   eve    — manager at Stellar (all permissions)
 *   dave   — regular buyer
 */
@Component
@Profile("demo")
@Order(2)
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final String PASS = "pass123";

    private final UserService userService;
    private final CompanyService companyService;
    private final EventService eventService;
    private final RaffleService raffleService;
    private final ICompanyRepository companyRepository;

    public DemoDataSeeder(UserService userService,
                          CompanyService companyService,
                          EventService eventService,
                          RaffleService raffleService,
                          ICompanyRepository companyRepository) {
        this.userService = userService;
        this.companyService = companyService;
        this.eventService = eventService;
        this.raffleService = raffleService;
        this.companyRepository = companyRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        logger.info("╔══════════════════════════════════════════╗");
        logger.info("║       DEMO DATA SEEDER — starting        ║");
        logger.info("╚══════════════════════════════════════════╝");

        try {
            seedUsers();
            String soundwaveId = seedSoundWave();
            seedStellar();
            printSummary(soundwaveId);
        } catch (Exception e) {
            logger.error("Demo seeder failed: {}", e.getMessage(), e);
        }
    }

    // ── Users ─────────────────────────────────────────────────────

    private void seedUsers() {
        for (String username : List.of("alice", "bob", "carol", "frank", "eve", "dave")) {
            Result<?> r = userService.register(username, PASS);
            if (r.isSuccess()) logger.info("  [user] '{}' created.", username);
            else logger.warn("  [user] '{}' skipped: {}", username, r.getErrorMessage());
        }
    }

    // ── SoundWave Entertainment ────────────────────────────────────

    private String seedSoundWave() {
        String aliceToken = token("alice");

        companyService.createCompany(aliceToken, "SoundWave Entertainment",
                "Premier live music and entertainment events across Israel");
        String companyId = companyId("SoundWave Entertainment");
        logger.info("  [company] 'SoundWave Entertainment' created (id={}).", companyId);

        // bob: MANAGE_EVENTS + VIEW_REPORTS
        companyService.appointManager(aliceToken, companyId, "bob",
                Set.of(CompanyPermission.MANAGE_EVENTS, CompanyPermission.VIEW_REPORTS));
        companyService.acceptNomination(token("bob"), companyId);
        logger.info("  [staff] bob appointed manager of SoundWave (MANAGE_EVENTS, VIEW_REPORTS).");

        // carol: MANAGE_POLICIES + MANAGE_DISCOUNTS
        companyService.appointManager(aliceToken, companyId, "carol",
                Set.of(CompanyPermission.MANAGE_POLICIES, CompanyPermission.MANAGE_DISCOUNTS));
        companyService.acceptNomination(token("carol"), companyId);
        logger.info("  [staff] carol appointed manager of SoundWave (MANAGE_POLICIES, MANAGE_DISCOUNTS).");

        String bobToken = token("bob");

        // Event 1 — Jazz Night (REGULAR, seated + standing)
        String jazzId = createEvent(aliceToken, companyId,
                "Jazz Night 2026",
                "A smooth evening of live jazz featuring Israel's finest artists and international guests",
                LocalDateTime.of(2026, 8, 15, 20, 0), "Music", "Tel Aviv Amphitheatre");
        eventService.createVenueMap(aliceToken, jazzId, "Tel Aviv Amphitheatre", List.of(
                new ZoneCreationDTO("Front Row",        ZoneType.SEATED,   250.0,   50),
                new ZoneCreationDTO("VIP Lounge",       ZoneType.SEATED,   400.0,   30),
                new ZoneCreationDTO("General Standing", ZoneType.STANDING, 120.0,  500)
        ));
        publish(aliceToken, jazzId);
        logger.info("  [event] 'Jazz Night 2026' published — REGULAR, seated + standing.");

        // Event 2 — Taylor Swift Tribute (QUEUE, standing only)
        String taylorId = createEvent(bobToken, companyId,
                "Taylor Swift Tribute Concert",
                "The biggest Taylor Swift tribute in Israel — three hours of Eras-style hits",
                LocalDateTime.of(2026, 9, 3, 19, 30), "Music", "Yarkon Park, Tel Aviv");
        eventService.createVenueMap(bobToken, taylorId, "Yarkon Park", List.of(
                new ZoneCreationDTO("Pit",     ZoneType.STANDING, 180.0,  800),
                new ZoneCreationDTO("Field A", ZoneType.STANDING, 130.0, 2000),
                new ZoneCreationDTO("Field B", ZoneType.STANDING, 100.0, 3000)
        ));
        eventService.setSaleMode(bobToken, taylorId, EventSaleMode.QUEUE);
        publish(bobToken, taylorId);
        logger.info("  [event] 'Taylor Swift Tribute Concert' published — QUEUE, standing zones.");

        // Event 3 — VIP Gala Dinner (RAFFLE, seated only, exclusive)
        String galaId = createEvent(aliceToken, companyId,
                "VIP Gala Dinner",
                "Exclusive charity gala with celebrity guests — only 100 seats allocated by lottery",
                LocalDateTime.of(2026, 10, 20, 19, 0), "Gala", "InterContinental Hotel, Tel Aviv");
        eventService.createVenueMap(aliceToken, galaId, "Grand Ballroom", List.of(
                new ZoneCreationDTO("Table A — Gold",   ZoneType.SEATED, 800.0, 40),
                new ZoneCreationDTO("Table B — Silver", ZoneType.SEATED, 600.0, 60)
        ));
        raffleService.createRaffle(aliceToken, galaId, companyId);
        publish(aliceToken, galaId);
        logger.info("  [event] 'VIP Gala Dinner' published — RAFFLE, seated only.");

        return companyId;
    }

    // ── Stellar Experiences ────────────────────────────────────────

    private void seedStellar() {
        String frankToken = token("frank");

        companyService.createCompany(frankToken, "Stellar Experiences",
                "World-class festivals, tech summits, and cultural experiences");
        String companyId = companyId("Stellar Experiences");
        logger.info("  [company] 'Stellar Experiences' created (id={}).", companyId);

        // eve: all permissions
        companyService.appointManager(frankToken, companyId, "eve",
                Set.of(CompanyPermission.MANAGE_EVENTS, CompanyPermission.MANAGE_POLICIES,
                       CompanyPermission.MANAGE_DISCOUNTS, CompanyPermission.VIEW_REPORTS));
        companyService.acceptNomination(token("eve"), companyId);
        logger.info("  [staff] eve appointed manager of Stellar (all permissions).");

        String eveToken = token("eve");

        // Event 4 — Rock Mega Festival (REGULAR, large mixed venue)
        String rockId = createEvent(frankToken, companyId,
                "Rock Mega Festival",
                "Three days of rock legends across three stages — the biggest festival of the year",
                LocalDateTime.of(2026, 7, 10, 16, 0), "Music", "HaPais Arena, Jerusalem");
        eventService.createVenueMap(frankToken, rockId, "HaPais Arena", List.of(
                new ZoneCreationDTO("Floor",             ZoneType.STANDING, 200.0, 5000),
                new ZoneCreationDTO("Bleachers North",   ZoneType.SEATED,   150.0, 1000),
                new ZoneCreationDTO("Bleachers South",   ZoneType.SEATED,   150.0, 1000),
                new ZoneCreationDTO("VIP Terrace",       ZoneType.SEATED,   500.0,  100)
        ));
        publish(frankToken, rockId);
        logger.info("  [event] 'Rock Mega Festival' published — REGULAR, large mixed venue.");

        // Event 5 — Tech Summit 2026 (RAFFLE, seated + standing)
        String techId = createEvent(eveToken, companyId,
                "Tech Summit 2026",
                "Israel's largest technology conference — limited raffle tickets for founders and investors",
                LocalDateTime.of(2026, 11, 5, 9, 0), "Technology", "Tel Aviv Convention Center");
        eventService.createVenueMap(eveToken, techId, "Convention Center", List.of(
                new ZoneCreationDTO("Main Hall",      ZoneType.SEATED,   350.0, 300),
                new ZoneCreationDTO("Startup Lounge", ZoneType.STANDING, 200.0, 150)
        ));
        raffleService.createRaffle(eveToken, techId, companyId);
        publish(eveToken, techId);
        logger.info("  [event] 'Tech Summit 2026' published — RAFFLE, seated + standing.");
    }

    // ── Helpers ───────────────────────────────────────────────────

    private String token(String username) {
        return userService.login(username, PASS)
                .getData()
                .orElseThrow(() -> new IllegalStateException("Cannot log in demo user: " + username));
    }

    private String companyId(String name) {
        return companyRepository.findAll().stream()
                .filter(c -> c.getName().equals(name))
                .findFirst()
                .map(ProductionCompany::getId)
                .orElseThrow(() -> new IllegalStateException("Company not found after creation: " + name));
    }

    private String createEvent(String token, String companyId,
                               String title, String description,
                               LocalDateTime date, String category, String location) {
        Result<String> r = eventService.createEvent(token, companyId, title, description, date, category, location);
        if (!r.isSuccess()) throw new IllegalStateException("Failed to create event '" + title + "': " + r.getErrorMessage());
        return r.getData().orElseThrow();
    }

    private void publish(String token, String eventId) {
        Result<Void> r = eventService.publishEvent(token, eventId);
        if (!r.isSuccess()) throw new IllegalStateException("Failed to publish event " + eventId + ": " + r.getErrorMessage());
    }

    private void printSummary(String soundwaveId) {
        logger.info("╔══════════════════════════════════════════════════════════════╗");
        logger.info("║               DEMO DATA SEEDED SUCCESSFULLY                 ║");
        logger.info("╠══════════════════════════════════════════════════════════════╣");
        logger.info("║  All users have password: pass123                           ║");
        logger.info("║                                                              ║");
        logger.info("║  alice  — founder, SoundWave Entertainment                  ║");
        logger.info("║  bob    — manager, SoundWave (events + reports)             ║");
        logger.info("║  carol  — manager, SoundWave (policies + discounts)         ║");
        logger.info("║  frank  — founder, Stellar Experiences                      ║");
        logger.info("║  eve    — manager, Stellar (all permissions)                ║");
        logger.info("║  dave   — regular buyer                                     ║");
        logger.info("║                                                              ║");
        logger.info("║  Events:                                                    ║");
        logger.info("║    Jazz Night 2026            REGULAR  seated+standing      ║");
        logger.info("║    Taylor Swift Tribute       QUEUE    standing             ║");
        logger.info("║    VIP Gala Dinner            RAFFLE   seated               ║");
        logger.info("║    Rock Mega Festival         REGULAR  large mixed          ║");
        logger.info("║    Tech Summit 2026           RAFFLE   seated+standing      ║");
        logger.info("╚══════════════════════════════════════════════════════════════╝");
    }
}
