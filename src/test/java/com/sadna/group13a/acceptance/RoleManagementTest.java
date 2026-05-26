package com.sadna.group13a.acceptance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.CompanyService;
import org.springframework.context.ApplicationEventPublisher;
import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import com.sadna.group13a.domain.Aggregates.Company.CompanyPermission;
import com.sadna.group13a.domain.Aggregates.User.Member;
import com.sadna.group13a.domain.Interfaces.ICompanyRepository;
import com.sadna.group13a.domain.Interfaces.IOrderHistoryRepository;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import com.sadna.group13a.infrastructure.AuthImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.CompanyRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.OrderHistoryRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.UserRepositoryImpl;
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
        companyRepository = new CompanyRepositoryImpl();
        userRepository = new UserRepositoryImpl();
        historyRepository = new OrderHistoryRepositoryImpl();
        authGateway = new AuthImpl();

        companyService = new CompanyService(companyRepository, userRepository, historyRepository, authGateway,
                new ObjectMapper(), e -> {});
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

        // Post-condition: circular appointment is rejected
        assertFalse(circularResult.isSuccess(), "Post: circular appointment must be blocked");
        assertTrue(circularResult.getErrorMessage().contains("already part of"), "Post: error must explain u1 is already in the hierarchy");
    }

    @Test
    @DisplayName("Given manager fired — Then manager and all their sub-appointees lose permissions")
    void GivenManagerFired_ThenSubTreeLosesPermissions() {
        setupUsersAndCompany("u1", "u2");
        userRepository.save(new Member("u3", "u3", "hash"));

        String t1 = authGateway.generateToken("u1");
        // Appoint u2 as OWNER (not MANAGER) so u2 can then appoint sub-managers
        companyService.appointOwner(t1, "c1", "u2");
        String t2 = authGateway.generateToken("u2");
        companyService.acceptNomination(t2, "c1");

        // u2 is now an OWNER and can appoint u3 as MANAGER — u3's appointedBy = u2
        companyService.appointManager(t2, "c1", "u3", Set.of(CompanyPermission.VIEW_REPORTS));
        String t3 = authGateway.generateToken("u3");
        companyService.acceptNomination(t3, "c1");
        // Pre-condition: u2 is a manager and u3 is a sub-appointee of u2
        ProductionCompany preCompany = companyRepository.findById("c1").get();
        assertTrue(preCompany.getStaff().containsKey("u2"), "Pre: u2 must be in staff before being fired");
        assertTrue(preCompany.getStaff().containsKey("u3"), "Pre: u3 (sub-appointee) must be in staff before u2 is fired");

        Result<Void> fireResult = companyService.fireManager(t1, "c1", "u2");
        assertTrue(fireResult.isSuccess(), "Firing must succeed");

        // Post-condition: u2 and their entire sub-tree (u3) lose all permissions
        ProductionCompany company = companyRepository.findById("c1").get();
        assertFalse(company.getStaff().containsKey("u2"), "Post: fired manager must be removed from staff");
        assertFalse(company.getStaff().containsKey("u3"), "Post: sub-appointee of fired manager must also be removed");
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
