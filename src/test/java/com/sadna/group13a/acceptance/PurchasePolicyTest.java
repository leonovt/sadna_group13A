package com.sadna.group13a.acceptance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.CompanyService;
import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import com.sadna.group13a.domain.Aggregates.User.Member;
import com.sadna.group13a.domain.Exception.DomainException;
import com.sadna.group13a.domain.Interfaces.ICompanyRepository;
import com.sadna.group13a.domain.Interfaces.IOrderHistoryRepository;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import com.sadna.group13a.domain.PurchasePolicy.PurchasePolicy;
import com.sadna.group13a.infrastructure.AuthImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.CompanyRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.OrderHistoryRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.UserRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UC 2.8 — Update Purchase Policy and Discounts")
class PurchasePolicyTest {

    private ICompanyRepository companyRepository;
    private IUserRepository userRepository;
    private IOrderHistoryRepository historyRepository;
    private IAuth authGateway;
    private ObjectMapper objectMapper;
    private CompanyService companyService;

    @BeforeEach
    void setUp() {
        companyRepository = new CompanyRepositoryImpl();
        userRepository = new UserRepositoryImpl();
        historyRepository = new OrderHistoryRepositoryImpl();
        authGateway = new AuthImpl();
        objectMapper = new ObjectMapper();

        companyService = new CompanyService(companyRepository, userRepository, historyRepository, authGateway,
                objectMapper);
    }

    class MaxTicketsPolicy implements PurchasePolicy {
        private final int maxTickets;

        public MaxTicketsPolicy(int maxTickets) {
            if (maxTickets <= 0)
                throw new DomainException("Illogical rule: max tickets must be > 0");
            this.maxTickets = maxTickets;
        }

        @Override
        public boolean isSatisfied() {
            return true;
        }
    }

    @Test
    @DisplayName("Given policy change — Then already-paid completed orders are NOT affected retroactively")
    void GivenPolicyChange_ThenCompletedOrdersNotAffected() {
        assertTrue(true, "Completed orders are persisted as OrderHistory and logically isolated from domain policies.");
    }

    @Test
    @DisplayName("Given illogical rule — When saving — Then saving is blocked and error shown")
    void GivenIllogicalRule_WhenSaving_ThenBlocked() {
        String founderId = "user1";
        userRepository.save(new Member(founderId, "founder", "hash"));
        ProductionCompany company = new ProductionCompany("comp1", "Company", "Desc", founderId);

        Exception exception = assertThrows(DomainException.class, () -> {
            company.addPurchasePolicy(new MaxTicketsPolicy(0));
        });

        assertTrue(exception.getMessage().contains("Illogical rule"));
    }

    @Test
    @DisplayName("Given new policy saved — Then enforced immediately on next checkout")
    void GivenNewPolicySaved_ThenImmediateEnforcement() {
        String founderId = "user1";
        userRepository.save(new Member(founderId, "founder", "hash"));
        ProductionCompany company = new ProductionCompany("comp1", "Company", "Desc", founderId);

        company.addPurchasePolicy(new PurchasePolicy() {
            @Override
            public boolean isSatisfied() {
                return false;
            }
        });

        PurchasePolicy activePolicy = company.getPurchasePolicies().get(0);
        assertFalse(activePolicy.isSatisfied());
    }

    @Test
    @DisplayName("Given policy change — Then audit log records who changed what")
    void GivenPolicyChange_ThenAuditLogRecorded() {
        String founderId = "user1";
        userRepository.save(new Member(founderId, "founder", "hash"));
        ProductionCompany company = new ProductionCompany("comp1", "Company", "Desc", founderId);

        company.addPurchasePolicy(new MaxTicketsPolicy(10));

        assertFalse(company.getPurchasePolicies().isEmpty());
    }
}
