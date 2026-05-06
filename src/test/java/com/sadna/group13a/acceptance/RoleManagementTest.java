package com.sadna.group13a.acceptance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.CompanyService;
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
                new ObjectMapper());
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

        Result<Void> result = companyService.appointOwner(t1, "c1", "u2");
        assertTrue(result.isSuccess());

        String t2 = authGateway.generateToken("u2");
        Result<Void> acceptResult = companyService.acceptNomination(t2, "c1");
        assertTrue(acceptResult.isSuccess());

        ProductionCompany company = companyRepository.findById("c1").get();
        assertTrue(company.isOwner("u2"));
    }

    @Test
    @DisplayName("Given circular appointment — When A appoints B and B tries to appoint A — Then blocked")
    void GivenCircularAppointment_WhenAttempted_ThenBlocked() {
        setupUsersAndCompany("u1", "u2");
        String t1 = authGateway.generateToken("u1");

        companyService.appointOwner(t1, "c1", "u2");

        String t2 = authGateway.generateToken("u2");
        companyService.acceptNomination(t2, "c1");

        Result<Void> circularResult = companyService.appointOwner(t2, "c1", "u1");

        assertFalse(circularResult.isSuccess());
        assertTrue(circularResult.getErrorMessage().contains("already part of"));
    }

    @Test
    @DisplayName("Given manager fired — Then manager and all their sub-appointees lose permissions")
    void GivenManagerFired_ThenSubTreeLosesPermissions() {
        setupUsersAndCompany("u1", "u2");
        userRepository.save(new Member("u3", "u3", "hash"));

        String t1 = authGateway.generateToken("u1");
        companyService.appointManager(t1, "c1", "u2", Set.of(CompanyPermission.MANAGE_EVENTS));

        String t2 = authGateway.generateToken("u2");
        companyService.acceptNomination(t2, "c1");

        companyService.appointManager(t2, "c1", "u3", Set.of(CompanyPermission.VIEW_REPORTS));
        String t3 = authGateway.generateToken("u3");
        companyService.acceptNomination(t3, "c1");

        Result<Void> fireResult = companyService.fireManager(t1, "c1", "u2");
        assertTrue(fireResult.isSuccess());

        ProductionCompany company = companyRepository.findById("c1").get();
        assertFalse(company.getStaff().containsKey("u2"));
        assertFalse(company.getStaff().containsKey("u3"));
    }

    @Test
    @DisplayName("Given manager — When updating permissions of their appointee — Then permissions update exactly")
    void GivenManager_WhenUpdatingPermissions_ThenSuccess() {
        setupUsersAndCompany("u1", "u2");
        String t1 = authGateway.generateToken("u1");

        companyService.appointManager(t1, "c1", "u2", Set.of(CompanyPermission.VIEW_REPORTS));
        String t2 = authGateway.generateToken("u2");
        companyService.acceptNomination(t2, "c1");

        Result<Void> updateResult = companyService.updatePermissions(t1, "c1", "u2",
                Set.of(CompanyPermission.VIEW_REPORTS, CompanyPermission.MANAGE_EVENTS));
        assertTrue(updateResult.isSuccess());

        ProductionCompany company = companyRepository.findById("c1").get();
        assertTrue(company.hasPermission("u2", CompanyPermission.MANAGE_EVENTS));
    }
}
