package com.sadna.group13a.acceptance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.CompanyService;
import com.sadna.group13a.domain.DomainServices.CompanyStaffDomainService;
import org.springframework.context.ApplicationEventPublisher;
import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import com.sadna.group13a.domain.Aggregates.Company.CompanyPermission;
import com.sadna.group13a.domain.Aggregates.Company.CompanyRole;
import com.sadna.group13a.domain.Aggregates.User.Member;
import com.sadna.group13a.domain.Interfaces.ICompanyRepository;
import com.sadna.group13a.domain.Interfaces.IOrderHistoryRepository;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import com.sadna.group13a.infrastructure.AuthImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.CompanyRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.OrderHistoryRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.UserRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.FakeUserJpaRepository;
import com.sadna.group13a.infrastructure.config.PersistenceConfig;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.FakeCompanyJpaRepository;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.FakeOrderHistoryJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UC 2.14 — Appointing Managers and Company Owners")
class RoleManagementTest {

    private ICompanyRepository companyRepository;
    private IUserRepository userRepository;
    private IOrderHistoryRepository historyRepository;
    private IAuth authGateway;
    private CompanyService companyService;

    @BeforeEach
    void setUp() {
        companyRepository = new CompanyRepositoryImpl(new FakeCompanyJpaRepository(), new PersistenceConfig().domainObjectMapper());
        userRepository = new UserRepositoryImpl(new FakeUserJpaRepository());
        historyRepository = new OrderHistoryRepositoryImpl(new FakeOrderHistoryJpaRepository(), new PersistenceConfig().domainObjectMapper());
        authGateway = new AuthImpl();

        companyService = new CompanyService(companyRepository, userRepository, historyRepository, authGateway,
                new ObjectMapper(), e -> {}, new CompanyStaffDomainService());
    }

    private void setupUsersAndCompany(String founderId, String otherUserId) {
        userRepository.save(new Member(founderId, founderId, "hash"));
        userRepository.save(new Member(otherUserId, otherUserId, "hash"));
        ProductionCompany company = new ProductionCompany("c1", "Company", "Desc", founderId);
        companyRepository.save(company);
    }

    @Test
    @DisplayName("Given owner — When appointing another user as owner — Then user gets owner role and permissions")
    void GivenOwner_WhenAppointingOwner_ThenSuccess() {
        setupUsersAndCompany("u1", "u2");
        String t1 = authGateway.generateToken("u1");
        // Pre-condition: u2 is not yet part of the company staff
        ProductionCompany preCompany = companyRepository.findById("c1").get();
        assertFalse(preCompany.isOwner("u2"), "Pre: u2 must not be an owner before appointment");

        Result<Void> result = companyService.appointOwner(t1, "c1", "u2");
        assertTrue(result.isSuccess(), "Appointment request must succeed");

        String t2 = authGateway.generateToken("u2");
        Result<Void> acceptResult = companyService.acceptNomination(t2, "c1");
        assertTrue(acceptResult.isSuccess(), "Nomination acceptance must succeed");

        // Post-condition: u2 now has owner role in the company
        ProductionCompany company = companyRepository.findById("c1").get();
        assertTrue(company.isOwner("u2"), "Post: u2 must be an owner after accepting the nomination");
    }

    @Test
    @DisplayName("Given circular appointment — When A appoints B and B tries to appoint A — Then blocked")
    void GivenCircularAppointment_WhenAttempted_ThenBlocked() {
        setupUsersAndCompany("u1", "u2");
        String t1 = authGateway.generateToken("u1");

        companyService.appointOwner(t1, "c1", "u2");
        String t2 = authGateway.generateToken("u2");
        companyService.acceptNomination(t2, "c1");
        // Pre-condition: u2 is now an owner and u1 is already in the staff tree
        ProductionCompany preCompany = companyRepository.findById("c1").get();
        assertTrue(preCompany.isOwner("u2"), "Pre: u2 must be an owner before attempting circular appointment");
        assertTrue(preCompany.getStaff().containsKey("u1"), "Pre: u1 must already be in the staff tree");

        Result<Void> circularResult = companyService.appointOwner(t2, "c1", "u1");

        // Post-condition: circular appointment is rejected and u1's existing role is unchanged
        assertFalse(circularResult.isSuccess(), "Post: circular appointment must be blocked");
        assertTrue(circularResult.getErrorMessage().contains("already part of"), "Post: error must explain u1 is already in the hierarchy");
        ProductionCompany postCompany = companyRepository.findById("c1").get();
        assertTrue(postCompany.getStaff().containsKey("u1"), "Post: u1 must remain in company staff unchanged after the blocked circular appointment");
    }

    @Test
    @DisplayName("Issue #367 — When an owner is fired — Then their managers are re-parented to the founder and stay active")
    void GivenOwnerWithManagers_WhenOwnerFired_ThenManagersReparentedToFounderAndStayActive() {
        setupUsersAndCompany("u1", "u2");
        userRepository.save(new Member("u3", "u3", "hash"));
        userRepository.save(new Member("u4", "u4", "hash"));

        String t1 = authGateway.generateToken("u1"); // founder
        // Founder appoints u2 as OWNER so u2 can appoint sub-managers
        companyService.appointOwner(t1, "c1", "u2");
        String t2 = authGateway.generateToken("u2");
        companyService.acceptNomination(t2, "c1");

        // u2 (owner) appoints u3 and u4 as MANAGERs — their appointedBy = u2
        companyService.appointManager(t2, "c1", "u3", Set.of(CompanyPermission.VIEW_REPORTS));
        companyService.acceptNomination(authGateway.generateToken("u3"), "c1");
        companyService.appointManager(t2, "c1", "u4",
                Set.of(CompanyPermission.MANAGE_EVENTS, CompanyPermission.MANAGE_DISCOUNTS));
        companyService.acceptNomination(authGateway.generateToken("u4"), "c1");

        // Pre-condition: u3 and u4 report to u2 (the owner)
        ProductionCompany preCompany = companyRepository.findById("c1").get();
        assertEquals("u2", preCompany.getStaff().get("u3").getAppointedByUserId(), "Pre: u3 reports to owner u2");
        assertEquals("u2", preCompany.getStaff().get("u4").getAppointedByUserId(), "Pre: u4 reports to owner u2");

        // Founder fires the owner u2
        Result<Void> result = companyService.removeOwner(t1, "c1", "u2");
        assertTrue(result.isSuccess(), "Removing the owner must succeed");

        ProductionCompany company = companyRepository.findById("c1").get();
        // The owner is gone
        assertFalse(company.getStaff().containsKey("u2"), "Post: fired owner must be removed from staff");
        // The managers are NOT deleted — they remain active staff members
        assertTrue(company.getStaff().containsKey("u3"), "Post: manager u3 must NOT be deleted when the owner is fired");
        assertTrue(company.getStaff().containsKey("u4"), "Post: manager u4 must NOT be deleted when the owner is fired");
        // They are re-parented to the founder
        assertEquals("u1", company.getStaff().get("u3").getAppointedByUserId(), "Post: u3 now reports to the founder");
        assertEquals("u1", company.getStaff().get("u4").getAppointedByUserId(), "Post: u4 now reports to the founder");
        // Role and permissions are intact
        assertEquals(CompanyRole.MANAGER, company.getStaff().get("u3").getRole(), "Post: u3 keeps the MANAGER role");
        assertTrue(company.getStaff().get("u3").getPermissions().contains(CompanyPermission.VIEW_REPORTS),
                "Post: u3 keeps its VIEW_REPORTS permission");
        assertEquals(Set.of(CompanyPermission.MANAGE_EVENTS, CompanyPermission.MANAGE_DISCOUNTS),
                company.getStaff().get("u4").getPermissions(), "Post: u4 keeps its exact permission set");
        // End-to-end: the managers' company role on their Member aggregate is preserved (still active staff)
        Member m3 = (Member) userRepository.findById("u3").get();
        Member m4 = (Member) userRepository.findById("u4").get();
        assertEquals(CompanyRole.MANAGER, m3.getRoleInCompany("c1"), "Post: u3's Member still holds the MANAGER role");
        assertEquals(CompanyRole.MANAGER, m4.getRoleInCompany("c1"), "Post: u4's Member still holds the MANAGER role");
        // The removed owner, by contrast, no longer holds the role
        Member m2 = (Member) userRepository.findById("u2").get();
        assertNull(m2.getRoleInCompany("c1"), "Post: the fired owner no longer holds a role in the company");
    }

    @Test
    @DisplayName("Issue #367 — Re-parented managers can still act under the founder after the owner is fired")
    void GivenOwnerFired_WhenFounderManagesReparentedManager_ThenSucceeds() {
        setupUsersAndCompany("u1", "u2");
        userRepository.save(new Member("u3", "u3", "hash"));

        String t1 = authGateway.generateToken("u1");
        companyService.appointOwner(t1, "c1", "u2");
        String t2 = authGateway.generateToken("u2");
        companyService.acceptNomination(t2, "c1");
        companyService.appointManager(t2, "c1", "u3", Set.of(CompanyPermission.VIEW_REPORTS));
        companyService.acceptNomination(authGateway.generateToken("u3"), "c1");

        companyService.removeOwner(t1, "c1", "u2");

        // The founder is now u3's direct appointer, so the founder can update u3's permissions
        Result<Void> update = companyService.updatePermissions(t1, "c1", "u3",
                Set.of(CompanyPermission.VIEW_REPORTS, CompanyPermission.MANAGE_EVENTS));
        assertTrue(update.isSuccess(), "Founder (new appointer) must be able to manage the re-parented manager");

        ProductionCompany company = companyRepository.findById("c1").get();
        assertTrue(company.hasPermission("u3", CompanyPermission.MANAGE_EVENTS),
                "Re-parented manager's permissions update under the founder");
    }

    @Test
    @DisplayName("Given a non-owner target — When removeOwner is called — Then it fails without touching staff")
    void GivenManagerTarget_WhenRemoveOwner_ThenFails() {
        setupUsersAndCompany("u1", "u2");
        String t1 = authGateway.generateToken("u1");
        // u2 is a MANAGER, not an OWNER
        companyService.appointManager(t1, "c1", "u2", Set.of(CompanyPermission.VIEW_REPORTS));
        companyService.acceptNomination(authGateway.generateToken("u2"), "c1");

        Result<Void> result = companyService.removeOwner(t1, "c1", "u2");

        assertFalse(result.isSuccess(), "removeOwner on a manager must fail");
        assertTrue(result.getErrorMessage().contains("not an Owner"), "Error must explain the target is not an owner");
        ProductionCompany company = companyRepository.findById("c1").get();
        assertTrue(company.getStaff().containsKey("u2"), "Manager must remain in staff after the failed removeOwner");
    }

    @Test
    @DisplayName("Given a target not in the company — When removeOwner is called — Then it fails cleanly")
    void GivenOutsiderTarget_WhenRemoveOwner_ThenFails() {
        setupUsersAndCompany("u1", "u2");
        String t1 = authGateway.generateToken("u1");
        // u2 exists as a user but was never added to the company staff

        Result<Void> result = companyService.removeOwner(t1, "c1", "u2");

        assertFalse(result.isSuccess(), "removeOwner on a non-member must fail");
        assertTrue(result.getErrorMessage().contains("not in this company"), "Error must explain the user is not in the company");
    }

    @Test
    @DisplayName("Given two owners — When both try to nominate the same user as manager — Then second appointment blocked")
    void GivenTwoOwners_WhenBothNominateSamePerson_ThenSecondIsBlocked() {
        setupUsersAndCompany("u1", "u2");
        userRepository.save(new Member("u3", "u3", "hash"));

        String t1 = authGateway.generateToken("u1");
        // Promote u2 to owner so both u1 and u2 are owners capable of nominating
        companyService.appointOwner(t1, "c1", "u2");
        String t2 = authGateway.generateToken("u2");
        companyService.acceptNomination(t2, "c1");

        // Pre-condition: u3 is not yet nominated by anyone
        ProductionCompany preCompany = companyRepository.findById("c1").get();
        assertFalse(preCompany.getStaff().containsKey("u3"), "Pre: u3 must not be in staff before any nomination");
        assertTrue(preCompany.getPendingAppointments().isEmpty(), "Pre: no pending appointments must exist");

        Result<Void> first = companyService.appointManager(t1, "c1", "u3", Set.of(CompanyPermission.MANAGE_EVENTS));
        Result<Void> second = companyService.appointManager(t2, "c1", "u3", Set.of(CompanyPermission.VIEW_REPORTS));

        // Post-condition: first nomination is accepted; second is rejected because u3 already has a pending appointment
        assertTrue(first.isSuccess(), "Post: first nomination must succeed");
        assertFalse(second.isSuccess(), "Post: second nomination for the same person must be rejected");
        assertTrue(second.getErrorMessage().contains("pending") || second.getErrorMessage().contains("appointment"),
                "Post: error must indicate u3 already has a pending appointment");
    }

    @Test
    @DisplayName("Given manager — When updating permissions of their appointee — Then permissions update exactly")
    void GivenManager_WhenUpdatingPermissions_ThenSuccess() {
        setupUsersAndCompany("u1", "u2");
        String t1 = authGateway.generateToken("u1");

        companyService.appointManager(t1, "c1", "u2", Set.of(CompanyPermission.VIEW_REPORTS));
        String t2 = authGateway.generateToken("u2");
        companyService.acceptNomination(t2, "c1");
        // Pre-condition: u2 has VIEW_REPORTS but NOT MANAGE_EVENTS permission yet
        ProductionCompany preCompany = companyRepository.findById("c1").get();
        assertTrue(preCompany.hasPermission("u2", CompanyPermission.VIEW_REPORTS), "Pre: u2 must have VIEW_REPORTS before update");
        assertFalse(preCompany.hasPermission("u2", CompanyPermission.MANAGE_EVENTS), "Pre: u2 must not have MANAGE_EVENTS before update");

        Result<Void> updateResult = companyService.updatePermissions(t1, "c1", "u2",
                Set.of(CompanyPermission.VIEW_REPORTS, CompanyPermission.MANAGE_EVENTS));
        assertTrue(updateResult.isSuccess(), "Permission update must succeed");

        // Post-condition: u2 now has MANAGE_EVENTS permission
        ProductionCompany company = companyRepository.findById("c1").get();
        assertTrue(company.hasPermission("u2", CompanyPermission.MANAGE_EVENTS), "Post: u2 must have MANAGE_EVENTS after update");
    }
}
