package com.sadna.group13a.application.Services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sadna.group13a.application.DTO.UserDTO;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Interfaces.IPaymentGateway;
import com.sadna.group13a.application.Interfaces.ITicketSupplier;
import com.sadna.group13a.application.Interfaces.TicketIssueRequest;
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
import com.sadna.group13a.infrastructure.config.PersistenceConfig;
import com.sadna.group13a.infrastructure.persistence.DatabaseConnectionManager;
import com.sadna.group13a.infrastructure.persistence.InMemoryDatabaseHealthProbe;
import com.sadna.group13a.infrastructure.persistence.PersistenceAvailabilityInvocationHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end robustness tests (SLR-5 / SLR-6): when the data store becomes unreachable,
 * service calls must return {@code Result.failure(...)} instead of throwing, and must
 * resume working automatically once the store is reachable again — no restart needed.
 *
 * <p>Infrastructure-level behaviour (the proxy itself, the connection manager itself) is
 * already covered by {@code PersistenceAvailabilityInvocationHandlerTest} and
 * {@code DatabaseConnectionManagerTest}. This test operates one level up: it wraps the
 * same in-memory repositories used by {@code OrderServiceIntegrationTest} in the real
 * availability proxy (mirroring what {@code RepositoryAvailabilityBeanPostProcessor} does
 * in production) and drives the connection state through the real
 * {@code DatabaseConnectionManager}, then calls the services exactly as a controller would.
 *
 * <p>No Spring context is loaded — everything is wired by hand.
 */
class DbConnectionRobustnessTest {

    private static final String USER_ID    = "user-robustness-001";
    private static final String COMPANY_ID = "company-robustness-001";
    private static final String EVENT_ID   = "event-robustness-001";
    private static final String ZONE_ID    = "zone-robustness-001";
    private static final String SEAT_ID    = "seat-robustness-001";
    private static final String SEAT_LABEL = "A-1";
    private static final double SEAT_PRICE = 100.0;

    private InMemoryDatabaseHealthProbe probe;
    private DatabaseConnectionManager   connectionManager;

    private IUserRepository         userRepository;
    private IOrderHistoryRepository historyRepository;
    private IActiveOrderRepository  orderRepository;
    private IEventRepository        eventRepository;
    private ICompanyRepository      companyRepository;
    private IQueueRepository        queueRepository;
    private IRaffleRepository       raffleRepository;

    private UserService  userService;
    private OrderService orderService;

    private StubAuth auth;

    @BeforeEach
    void setUp() {
        probe             = new InMemoryDatabaseHealthProbe();
        connectionManager = new DatabaseConnectionManager(probe);

        // Real in-memory repositories, each wrapped in the same availability proxy that
        // RepositoryAvailabilityBeanPostProcessor installs in production, so that a "DB
        // down" connectionManager state is enforced on every repository call made by the
        // services under test — exactly as it would be in the running application.
        userRepository     = wrapWithAvailability(
                new UserRepositoryImpl(new FakeUserJpaRepository()), IUserRepository.class);
        historyRepository  = wrapWithAvailability(
                new OrderHistoryRepositoryImpl(new FakeOrderHistoryJpaRepository(), new PersistenceConfig().domainObjectMapper()),
                IOrderHistoryRepository.class);
        orderRepository    = wrapWithAvailability(
                new ActiveOrderRepositoryImpl(new FakeActiveOrderJpaRepository(), new PersistenceConfig().domainObjectMapper()),
                IActiveOrderRepository.class);
        eventRepository    = wrapWithAvailability(
                new EventRepositoryImpl(new FakeEventJpaRepository(), new PersistenceConfig().domainObjectMapper()),
                IEventRepository.class);
        companyRepository   = wrapWithAvailability(
                new CompanyRepositoryImpl(new FakeCompanyJpaRepository(), new PersistenceConfig().domainObjectMapper()),
                ICompanyRepository.class);
        queueRepository    = wrapWithAvailability(new QueueRepositoryImpl(), IQueueRepository.class);
        raffleRepository   = wrapWithAvailability(
                new RaffleRepositoryImpl(new FakeRaffleJpaRepository(), new PersistenceConfig().domainObjectMapper()),
                IRaffleRepository.class);

        auth = new StubAuth();

        userService = new UserService(
                userRepository, auth, new PasswordEncoderImpl(), historyRepository,
                new ObjectMapper().findAndRegisterModules());

        orderService = new OrderService(
                orderRepository,
                historyRepository,
                eventRepository,
                companyRepository,
                queueRepository,
                raffleRepository,
                new StubPaymentGateway(),
                new StubTicketSupplier(),
                userRepository,
                auth,
                new CheckoutDomainService(),
                new TicketingAccessDomainService(),
                event -> { },
                new CartDomainService(),
                null,
                new SystemLogService());

        // Seed data while the store is still healthy.
        seedEventCompanyAndUser();
    }

    /**
     * Wraps {@code target} in the production {@link PersistenceAvailabilityInvocationHandler}
     * proxy, mirroring {@code RepositoryAvailabilityBeanPostProcessor}.
     */
    private <T> T wrapWithAvailability(Object target, Class<T> repositoryInterface) {
        Set<Class<?>> interfaces = ClassUtils.getAllInterfacesForClassAsSet(target.getClass());
        Object proxy = Proxy.newProxyInstance(
                target.getClass().getClassLoader(),
                interfaces.toArray(new Class<?>[0]),
                new PersistenceAvailabilityInvocationHandler(target, connectionManager));
        return repositoryInterface.cast(proxy);
    }

    private void seedEventCompanyAndUser() {
        ProductionCompany company = new ProductionCompany(
                COMPANY_ID, "Robustness Test Productions", "Testing", USER_ID);
        companyRepository.save(company);

        Seat seat = new Seat(SEAT_ID, SEAT_LABEL);
        SeatedZone zone = new SeatedZone(ZONE_ID, "General", SEAT_PRICE, List.of(seat));
        VenueMap venueMap = new VenueMap("vm-robustness-001", "Test Hall", List.of(zone));

        Event event = new Event(
                EVENT_ID, "Robustness Test Event", "DB outage drill", COMPANY_ID,
                LocalDateTime.now().plusDays(10), "Test");
        event.setVenueMap(venueMap);
        event.publish();
        eventRepository.save(event);

        userRepository.save(new Member(USER_ID, "robustnessUser", "irrelevant-hash"));
    }

    private void simulateDbDown() {
        probe.simulateOutage();
        connectionManager.monitorConnection();
        assertFalse(connectionManager.isConnected(), "Test setup: connection manager must report disconnected");
    }

    private void simulateDbRestored() {
        probe.simulateRestore();
        connectionManager.monitorConnection();
        assertTrue(connectionManager.isConnected(), "Test setup: connection manager must report connected");
    }

    @Test
    @DisplayName("UserService.register returns Result.failure (does not throw) while the DB is down")
    void givenDbDown_whenRegister_thenCleanFailureNotException() {
        simulateDbDown();

        Result<UserDTO> result = assertDoesNotThrow(() -> userService.register("newUser", "password123"));

        assertFalse(result.isSuccess(), "register must fail gracefully while the DB is unreachable");
    }

    @Test
    @DisplayName("OrderService.addItemToCart returns Result.failure (does not throw) while the DB is down")
    void givenDbDown_whenAddItemToCart_thenCleanFailureNotException() {
        String token = auth.generateToken(USER_ID);
        simulateDbDown();

        Result<String> result = assertDoesNotThrow(
                () -> orderService.addItemToCart(token, EVENT_ID, ZONE_ID, SEAT_ID));

        assertFalse(result.isSuccess(), "addItemToCart must fail gracefully while the DB is unreachable");
    }

    @Test
    @DisplayName("OrderService.cancelCart returns Result.failure (does not throw) while the DB is down")
    void givenDbDown_whenCancelCart_thenCleanFailureNotException() {
        String token = auth.generateToken(USER_ID);
        simulateDbDown();

        Result<Void> result = assertDoesNotThrow(() -> orderService.cancelCart(token));

        assertFalse(result.isSuccess(), "cancelCart must fail gracefully while the DB is unreachable");
    }

    @Test
    @DisplayName("After the DB comes back, register() succeeds again with no restart")
    void givenDbRecovers_whenRegisterAgain_thenSucceeds() {
        simulateDbDown();
        assertFalse(userService.register("duringOutage", "password123").isSuccess());

        simulateDbRestored();

        Result<UserDTO> result = userService.register("afterRecovery", "password123");

        assertTrue(result.isSuccess(), "register must succeed once the DB is reachable again");
        assertTrue(userRepository.findByUsername("afterRecovery").isPresent());
    }

    @Test
    @DisplayName("After the DB comes back, addItemToCart() succeeds again with no restart")
    void givenDbRecovers_whenAddItemToCartAgain_thenSucceeds() {
        String token = auth.generateToken(USER_ID);

        simulateDbDown();
        assertFalse(orderService.addItemToCart(token, EVENT_ID, ZONE_ID, SEAT_ID).isSuccess());

        simulateDbRestored();

        Result<String> result = orderService.addItemToCart(token, EVENT_ID, ZONE_ID, SEAT_ID);

        assertTrue(result.isSuccess(), "addItemToCart must succeed once the DB is reachable again");
        assertTrue(orderRepository.findById(result.getOrThrow()).isPresent());
    }

    // ── Test infrastructure ───────────────────────────────────────────────────────

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

    static class StubPaymentGateway implements IPaymentGateway {
        @Override public boolean isConnected() { return true; }
        @Override public Result<String> processPayment(double amount, String paymentDetails) {
            return Result.success("TXN-" + UUID.randomUUID());
        }
        @Override public Result<Void> refundPayment(String transactionId) { return Result.success(); }
        @Override public Result<Void> refundPartial(String transactionId, double amount) { return Result.success(); }
    }

    static class StubTicketSupplier implements ITicketSupplier {
        @Override public boolean isConnected() { return true; }
        @Override public Result<List<String>> issueTickets(String customerId, List<TicketIssueRequest> requests) {
            List<String> codes = new java.util.ArrayList<>();
            for (int i = 0; i < requests.size(); i++) codes.add("TICKET-" + UUID.randomUUID());
            return Result.success(codes);
        }
        @Override public Result<Void> cancelTickets(List<String> ticketCodes) { return Result.success(); }
    }
}
