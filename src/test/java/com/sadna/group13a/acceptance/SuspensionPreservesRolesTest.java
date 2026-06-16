package com.sadna.group13a.acceptance;

import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.AdminService;
import com.sadna.group13a.application.Services.SystemLogService;
import com.sadna.group13a.application.EventListeners.CompanyEventListener;
import com.sadna.group13a.domain.Aggregates.Admin.Admin;
import com.sadna.group13a.domain.Aggregates.Company.CompanyPermission;
import com.sadna.group13a.domain.Aggregates.Company.CompanyRole;
import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import com.sadna.group13a.domain.Aggregates.User.Member;
import com.sadna.group13a.domain.Events.UserBannedEvent;
import com.sadna.group13a.infrastructure.AuthImpl;
import com.sadna.group13a.infrastructure.StubPaymentGateway;
import com.sadna.group13a.infrastructure.RepositoryImpl.AdminRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.FakeAdminJpaRepository;
import com.sadna.group13a.infrastructure.config.PersistenceConfig;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.FakeEventJpaRepository;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.FakeOrderHistoryJpaRepository;
import com.sadna.group13a.infrastructure.RepositoryImpl.CompanyRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.EventRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.OrderHistoryRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.QueueRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.UserRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.FakeUserJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * UC 11.6.7 — issue #189.
 *
 * A temporary (or permanent) suspension must keep the user view-only WITHOUT revoking
 * their company appointments. A full member cancellation (deactivate) must still cascade
 * and remove those appointments.
 *
 * The real {@link CompanyEventListener} only reacts to {@link UserBannedEvent}, so this test
 * routes published events to it the same way Spring would — proving that suspension
 * (which now emits a UserSuspendedEvent) leaves the company role tree intact.
 */
@DisplayName("UC 11.6.7 — Suspension preserves company roles")
class SuspensionPreservesRolesTest {

    private UserRepositoryImpl userRepo;
    private CompanyRepositoryImpl companyRepo;
    private AuthImpl auth;
    private CompanyEventListener companyListener;
    private AdminService adminService;

    private static final String ADMIN_ID = "admin1";

    @BeforeEach
    void setUp() {
        userRepo = new UserRepositoryImpl(new FakeUserJpaRepository(), new PersistenceConfig().domainObjectMapper());
        companyRepo = new CompanyRepositoryImpl();
        AdminRepositoryImpl adminRepo = new AdminRepositoryImpl(new FakeAdminJpaRepository(), new PersistenceConfig().domainObjectMapper());
        auth = new AuthImpl();
        SystemLogService log = mock(SystemLogService.class);
        companyListener = new CompanyEventListener(companyRepo);

        // Route domain events to the real listener exactly like Spring's @EventListener wiring:
        // the listener only handles UserBannedEvent (cancellation), never UserSuspendedEvent.
        ApplicationEventPublisher publisher = event -> {
            if (event instanceof UserBannedEvent ub) {
                companyListener.onUserBanned(ub);
            }
        };

        userRepo.save(new Member(ADMIN_ID, "admin", "hash"));
        adminRepo.save(new Admin("admin-rec-1", ADMIN_ID));

        adminService = new AdminService(
                userRepo, adminRepo, new EventRepositoryImpl(new FakeEventJpaRepository(), new PersistenceConfig().domainObjectMapper()), companyRepo,
                new QueueRepositoryImpl(), new OrderHistoryRepositoryImpl(new FakeOrderHistoryJpaRepository(), new PersistenceConfig().domainObjectMapper()),
                new StubPaymentGateway(), auth, publisher, log);
    }

    /** Seeds a company whose founder appointed an accepted manager, and returns that company. */
    private ProductionCompany seedCompanyWithManager() {
        userRepo.save(new Member("founder1", "founder", "hash"));
        userRepo.save(new Member("mgr1", "manager", "hash"));
        ProductionCompany company = new ProductionCompany("c1", "Comp", "Desc", "founder1");
        company.nominateStaff("founder1", "mgr1", CompanyRole.MANAGER, Set.of(CompanyPermission.MANAGE_EVENTS));
        company.acceptNomination("mgr1");
        companyRepo.save(company);
        return company;
    }

    @Test
    @DisplayName("Suspending a manager keeps their company role and makes them view-only")
    void givenManager_whenSuspended_thenRolePreservedAndViewOnly() {
        seedCompanyWithManager();
        String adminToken = auth.generateToken(ADMIN_ID);
        assertTrue(companyRepo.findById("c1").orElseThrow().getStaff().containsKey("mgr1"),
                "Pre: manager must be part of the company staff");

        Result<Void> result = adminService.suspendUser(adminToken, "manager", 7L);

        assertTrue(result.isSuccess(), "Suspension must succeed");
        assertTrue(companyRepo.findById("c1").orElseThrow().getStaff().containsKey("mgr1"),
                "Post: suspended manager must KEEP their company role");
        assertFalse(userRepo.findById("mgr1").orElseThrow().isActive(),
                "Post: suspended user is inactive (view-only)");
        assertTrue(userRepo.findById("mgr1").orElseThrow().isSuspended(),
                "Post: user is flagged as suspended");
    }

    @Test
    @DisplayName("Lifting a suspension restores active status while the role was never lost")
    void givenSuspendedManager_whenLifted_thenActiveAndStillStaff() {
        seedCompanyWithManager();
        String adminToken = auth.generateToken(ADMIN_ID);
        adminService.suspendUser(adminToken, "manager", 7L);

        Result<Void> result = adminService.liftSuspension(adminToken, "manager");

        assertTrue(result.isSuccess(), "Lifting suspension must succeed");
        assertTrue(userRepo.findById("mgr1").orElseThrow().isActive(), "Post: user active again");
        assertTrue(companyRepo.findById("c1").orElseThrow().getStaff().containsKey("mgr1"),
                "Post: company role intact after the full suspend/lift cycle");
    }

    @Test
    @DisplayName("Contrast: fully cancelling (deactivating) a manager DOES revoke their company role")
    void givenManager_whenDeactivated_thenRoleRevoked() {
        seedCompanyWithManager();
        String adminToken = auth.generateToken(ADMIN_ID);

        Result<Void> result = adminService.deactivateUser(adminToken, "manager");

        assertTrue(result.isSuccess(), "Deactivation must succeed");
        assertFalse(companyRepo.findById("c1").orElseThrow().getStaff().containsKey("mgr1"),
                "Post: a cancelled member is removed from company staff (cascade via UserBannedEvent)");
    }
}
