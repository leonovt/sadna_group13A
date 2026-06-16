package com.sadna.group13a.acceptance;

import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.AdminService;
import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import com.sadna.group13a.domain.Aggregates.OrderHistory.OrderHistory;
import com.sadna.group13a.domain.Aggregates.OrderHistory.OrderHistoryItem;
import com.sadna.group13a.domain.Aggregates.Admin.Admin;
import com.sadna.group13a.application.Services.SystemLogService;
import com.sadna.group13a.domain.Aggregates.User.Member;
import com.sadna.group13a.domain.Interfaces.IAdminRepository;
import com.sadna.group13a.domain.Interfaces.ICompanyRepository;
import com.sadna.group13a.domain.Interfaces.IOrderHistoryRepository;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import com.sadna.group13a.infrastructure.AuthImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.CompanyRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.FakeAdminJpaRepository;
import com.sadna.group13a.infrastructure.config.PersistenceConfig;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.FakeOrderHistoryJpaRepository;
import com.sadna.group13a.infrastructure.RepositoryImpl.OrderHistoryRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.UserRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.FakeUserJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("UC 2.18 — Cancel Subscriber from Platform")
class SubscriberCancellationTest {

    private IUserRepository userRepository;
    private ICompanyRepository companyRepository;
    private IOrderHistoryRepository historyRepository;
    private IAuth authGateway;
    private AdminService adminService;
    private ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        userRepository = new UserRepositoryImpl(new FakeUserJpaRepository(), new PersistenceConfig().domainObjectMapper());
        companyRepository = new CompanyRepositoryImpl();
        historyRepository = new OrderHistoryRepositoryImpl(new FakeOrderHistoryJpaRepository(), new PersistenceConfig().domainObjectMapper());
        authGateway = new AuthImpl();
        eventPublisher = mock(ApplicationEventPublisher.class);

        userRepository.save(new Member("admin1", "admin", "hash"));
        IAdminRepository adminRepository = new com.sadna.group13a.infrastructure.RepositoryImpl.AdminRepositoryImpl(
                new FakeAdminJpaRepository(), new PersistenceConfig().domainObjectMapper());
        adminRepository.save(new Admin("admin_rec_1", "admin1"));

        SystemLogService systemLogService = mock(SystemLogService.class);
        adminService = new AdminService(
                userRepository, adminRepository, null, companyRepository,
                null, historyRepository, new com.sadna.group13a.infrastructure.StubPaymentGateway(),
                authGateway, eventPublisher, systemLogService);
    }

    @Test
    @DisplayName("Given admin — When cancelling user — Then user becomes inactive and cannot login")
    void GivenAdmin_WhenCancellingUser_ThenUserInactive() {
        String adminToken = authGateway.generateToken("admin1");
        userRepository.save(new Member("u1", "user", "hash"));
        // Pre-condition: target user is currently active
        assertTrue(((Member) userRepository.findById("u1").get()).isActive(), "Pre: user must be active before cancellation");

        Result<Void> result = adminService.deactivateUser(adminToken, "user");
        assertTrue(result.isSuccess(), "Deactivation must succeed");

        // Post-condition: user is inactive and can no longer log in
        Member user = (Member) userRepository.findById("u1").get();
        assertFalse(user.isActive(), "Post: user must be inactive after admin cancellation");
    }

    @Test
    @DisplayName("Given user with roles — When cancelled — Then roles and permissions revoked safely")
    void GivenUserWithRoles_WhenCancelled_ThenRolesRevoked() {
        String adminToken = authGateway.generateToken("admin1");
        userRepository.save(new Member("u1", "founder", "hash"));
        ProductionCompany company = new ProductionCompany("c1", "Comp", "Desc", "u1");
        companyRepository.save(company);
        // Pre-condition: user is active and has a company founder role
        assertTrue(((Member) userRepository.findById("u1").get()).isActive(), "Pre: user must be active before cancellation");

        Result<Void> result = adminService.deactivateUser(adminToken, "founder");
        assertTrue(result.isSuccess(), "Deactivation must succeed");

        // Post-condition: account is deactivated — the user can no longer perform actions
        assertFalse(((Member) userRepository.findById("u1").get()).isActive(), "Post: user must be inactive after cancellation");
        // Post-condition: a UserBannedEvent is published so downstream listeners (CompanyEventListener)
        // revoke the user's roles in all companies they belonged to
        verify(eventPublisher).publishEvent(any(com.sadna.group13a.domain.Events.UserBannedEvent.class));
    }

    @Test
    @DisplayName("Given user cancelled — Then their order history is preserved completely")
    void GivenUserCancelled_ThenOrderHistoryPreserved() {
        String adminToken = authGateway.generateToken("admin1");
        userRepository.save(new Member("u1", "user", "hash"));

        OrderHistoryItem item = new OrderHistoryItem("e1", "Event", LocalDateTime.now(), "Company", "c1", "VIP", "A1", 50.0);
        OrderHistory order = new OrderHistory("r1", "u1", LocalDateTime.now(), 50.0, java.util.List.of(item));
        historyRepository.save(order);
        // Pre-condition: user is active and has at least one order in history
        assertTrue(((Member) userRepository.findById("u1").get()).isActive(), "Pre: user must be active before cancellation");
        assertFalse(historyRepository.findByUserId("u1").isEmpty(), "Pre: user must have order history before cancellation");

        adminService.deactivateUser(adminToken, "user");

        // Post-condition: order history is preserved even after account deactivation
        assertFalse(historyRepository.findByUserId("u1").isEmpty(), "Post: order history must be preserved after user cancellation");
    }

    @Test
    @DisplayName("Given unauthorized user — When cancelling someone — Then blocked")
    void GivenUnauthorizedUser_WhenCancelling_ThenBlocked() {
        userRepository.save(new Member("u1", "user", "hash"));
        String token = authGateway.generateToken("u1");
        // Pre-condition: the acting user is not an admin
        assertFalse(((Member) userRepository.findById("u1").get()).getRole().name().equals("ADMIN"),
                "Pre: acting user must not be an admin");

        Result<Void> result = adminService.deactivateUser(token, "u2");

        // Post-condition: cancellation is blocked with an access denied error
        assertFalse(result.isSuccess(), "Post: non-admin must not be able to cancel a subscriber");
        assertNotNull(result.getErrorMessage(), "Post: error message must be returned");
    }
}
