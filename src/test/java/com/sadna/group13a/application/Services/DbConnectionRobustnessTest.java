package com.sadna.group13a.application.Services;

import com.sadna.group13a.application.DTO.UserDTO;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import com.sadna.group13a.domain.Aggregates.Event.Event;
import com.sadna.group13a.domain.Aggregates.Event.Seat;
import com.sadna.group13a.domain.Aggregates.Event.SeatedZone;
import com.sadna.group13a.domain.Aggregates.Event.VenueMap;
import com.sadna.group13a.domain.Aggregates.User.Member;
import com.sadna.group13a.domain.DomainServices.CartDomainService;
import com.sadna.group13a.domain.DomainServices.CheckoutDomainService;
import com.sadna.group13a.domain.DomainServices.TicketingAccessDomainService;
import com.sadna.group13a.domain.Interfaces.IActiveOrderRepository;
import com.sadna.group13a.domain.Interfaces.ICompanyRepository;
import com.sadna.group13a.domain.Interfaces.IEventRepository;
import com.sadna.group13a.domain.Interfaces.IOrderHistoryRepository;
import com.sadna.group13a.domain.Interfaces.IQueueRepository;
import com.sadna.group13a.domain.Interfaces.IRaffleRepository;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import com.sadna.group13a.infrastructure.PasswordEncoderImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.ActiveOrderRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.CompanyRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.EventRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.OrderHistoryRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.QueueRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.RaffleRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.UserRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.FakeActiveOrderJpaRepository;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.FakeCompanyJpaRepository;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.FakeEventJpaRepository;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.FakeOrderHistoryJpaRepository;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.FakeRaffleJpaRepository;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.FakeUserJpaRepository;
import com.sadna.group13a.infrastructure.StubPaymentGateway;
import com.sadna.group13a.infrastructure.StubTicketSupplier;
import com.sadna.group13a.infrastructure.config.PersistenceConfig;
import com.sadna.group13a.infrastructure.persistence.DatabaseConnectionManager;
import com.sadna.group13a.infrastructure.persistence.PersistenceAvailabilityInvocationHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end (service-layer) tests for DB connection loss and recovery (SLR-5 / SLR-6).
 *
 * <p>Repositories are wrapped with the same {@link PersistenceAvailabilityInvocationHandler}
 * proxy that {@code RepositoryAvailabilityBeanPostProcessor} installs in production, backed by
 * a real {@link DatabaseConnectionManager} whose availability is flipped directly via
 * {@code markUnavailable()}/{@code markAvailable()} — no Spring context is loaded.
 */
class DbConnectionRobustnessTest {

    private static final String USER_ID     = "user-robustness-001";
    private static final String COMPANY_ID  = "company-robustness-001";
    private static final String EVENT_ID    = "event-robustness-001";
    private static final String ZONE_ID     = "zone-robustness-001";
    private static final String SEAT_ID     = "seat-robustness-001";
    private static final String SEAT_LABEL  = "A-1";
    private static final String ZONE_NAME   = "VIP Section";
    private static final double SEAT_PRICE  = 150.0;
    private static final String VALID_TOKEN = "robustness-test-token-abc";

    private DatabaseConnectionManager connectionManager;

    private IUserRepository        userRepo;
    private IActiveOrderRepository orderRepo;
    private IOrderHistoryRepository historyRepo;
    private IEventRepository       eventRepo;
    private ICompanyRepository     companyRepo;

    private UserService  userService;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        connectionManager = new DatabaseConnectionManager(() -> true);

        userRepo    = guarded(new UserRepositoryImpl(new FakeUserJpaRepository()), IUserRepository.class);
        orderRepo   = guarded(new ActiveOrderRepositoryImpl(new FakeActiveOrderJpaRepository(), new PersistenceConfig().domainObjectMapper()), IActiveOrderRepository.class);
        historyRepo = guarded(new OrderHistoryRepositoryImpl(new FakeOrderHistoryJpaRepository(), new PersistenceConfig().domainObjectMapper()), IOrderHistoryRepository.class);
        eventRepo   = guarded(new EventRepositoryImpl(new FakeEventJpaRepository(), new PersistenceConfig().domainObjectMapper()), IEventRepository.class);
        companyRepo = guarded(new CompanyRepositoryImpl(new FakeCompanyJpaRepository(), new PersistenceConfig().domainObjectMapper()), ICompanyRepository.class);
        IQueueRepository  queueRepo  = guarded(new QueueRepositoryImpl(), IQueueRepository.class);
        IRaffleRepository raffleRepo = guarded(new RaffleRepositoryImpl(new FakeRaffleJpaRepository(), new PersistenceConfig().domainObjectMapper()), IRaffleRepository.class);

        IAuth auth = new StubAuth(USER_ID, VALID_TOKEN);

        userService = new UserService(
                userRepo, auth, new PasswordEncoderImpl(), historyRepo,
                new PersistenceConfig().domainObjectMapper());

        orderService = new OrderService(
                orderRepo, historyRepo, eventRepo, companyRepo, queueRepo, raffleRepo,
                new StubPaymentGateway(), new StubTicketSupplier(), userRepo, auth,
                new CheckoutDomainService(), new TicketingAccessDomainService(),
                event -> { }, // no-op publisher; notifications are not under test here
                new CartDomainService(), null, new SystemLogService());

        seedEventAndCompany();
        seedUser();
    }

    /** Wraps a repository with the same connectivity guard used in production. */
    @SuppressWarnings("unchecked")
    private <T> T guarded(T target, Class<T> iface) {
        return (T) Proxy.newProxyInstance(
                iface.getClassLoader(),
                new Class<?>[]{iface},
                new PersistenceAvailabilityInvocationHandler(target, connectionManager));
    }

    private void seedEventAndCompany() {
        ProductionCompany company = new ProductionCompany(
                COMPANY_ID, "Robustness Events Ltd.", "Concerts and live shows", USER_ID);
        companyRepo.save(company);

        Seat       seat     = new Seat(SEAT_ID, SEAT_LABEL);
        SeatedZone zone     = new SeatedZone(ZONE_ID, ZONE_NAME, SEAT_PRICE, List.of(seat));
        VenueMap   venueMap = new VenueMap("vm-001", "The Big Arena", List.of(zone));

        Event event = new Event(
                EVENT_ID, "Robustness Concert", "DB outage robustness test event",
                COMPANY_ID, LocalDateTime.now().plusDays(30), "Music");
        event.setVenueMap(venueMap);
        event.publish();

        eventRepo.save(event);
    }

    private void seedUser() {
        userRepo.save(new Member(USER_ID, "robustnessUser", "hashed-pw-irrelevant-in-this-context"));
    }

    // ── DB unavailable: callers get a clean failure, never an unchecked exception ──

    @Test
    @DisplayName("Given the DB is unavailable, UserService.register fails cleanly without throwing")
    void givenDbUnavailable_whenRegisterCalled_thenReturnsFailureWithoutThrowing() {
        connectionManager.markUnavailable();

        Result<UserDTO> result = assertDoesNotThrow(() -> userService.register("newUser", "password123"));

        assertFalse(result.isSuccess());
    }

    @Test
    @DisplayName("Given the DB is unavailable, OrderService.addItemToCart fails cleanly without throwing")
    void givenDbUnavailable_whenAddItemToCartCalled_thenReturnsFailureWithoutThrowing() {
        connectionManager.markUnavailable();

        Result<String> result = assertDoesNotThrow(() ->
                orderService.addItemToCart(VALID_TOKEN, EVENT_ID, ZONE_ID, SEAT_ID));

        assertFalse(result.isSuccess());
    }

    // ── DB recovers: the same calls succeed normally afterwards ───────────────────

    @Test
    @DisplayName("Given the DB recovers after an outage, UserService.register succeeds again")
    void givenDbRecoversAfterOutage_whenRegisterCalledAgain_thenSucceeds() {
        connectionManager.markUnavailable();
        Result<UserDTO> duringOutage = userService.register("recoveredUser", "password123");
        assertFalse(duringOutage.isSuccess());

        connectionManager.markAvailable();
        Result<UserDTO> afterRecovery = userService.register("recoveredUser", "password123");

        assertTrue(afterRecovery.isSuccess());
    }

    @Test
    @DisplayName("Given the DB recovers after an outage, OrderService.addItemToCart succeeds again")
    void givenDbRecoversAfterOutage_whenAddItemToCartCalledAgain_thenSucceeds() {
        connectionManager.markUnavailable();
        Result<String> duringOutage = orderService.addItemToCart(VALID_TOKEN, EVENT_ID, ZONE_ID, SEAT_ID);
        assertFalse(duringOutage.isSuccess());

        connectionManager.markAvailable();
        Result<String> afterRecovery = orderService.addItemToCart(VALID_TOKEN, EVENT_ID, ZONE_ID, SEAT_ID);

        assertTrue(afterRecovery.isSuccess());
    }

    // ── Test infrastructure ────────────────────────────────────────────────────

    /** A deterministic {@link IAuth} stub validating a single pre-configured token/userId pair. */
    static class StubAuth implements IAuth {

        private final String userId;
        private final String validToken;

        StubAuth(String userId, String validToken) {
            this.userId = userId;
            this.validToken = validToken;
        }

        @Override public String generateToken(String uid) { return validToken; }

        @Override public boolean validateToken(String token) { return validToken.equals(token); }

        @Override public String extractUserId(String token) { return userId; }
    }
}
