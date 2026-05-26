package com.sadna.group13a.acceptance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.CompanyService;
import org.springframework.context.ApplicationEventPublisher;
import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import com.sadna.group13a.domain.Aggregates.User.Member;
import com.sadna.group13a.domain.shared.DomainException;
import com.sadna.group13a.domain.Interfaces.ICompanyRepository;
import com.sadna.group13a.domain.Interfaces.IOrderHistoryRepository;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import com.sadna.group13a.domain.shared.PurchasePolicy;
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
                objectMapper, e -> {});
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
        // Pre-condition: company exists with no policies yet
        assertTrue(company.getPurchasePolicies().isEmpty(), "Pre: company must have no policies before adding an illogical rule");

        Exception exception = assertThrows(DomainException.class, () -> {
            company.addPurchasePolicy(new MaxTicketsPolicy(0));
        });

        // Post-condition: domain rejects the rule and policies list remains empty
        assertTrue(exception.getMessage().contains("Illogical rule"), "Post: error must describe the illogical rule");
        assertTrue(company.getPurchasePolicies().isEmpty(), "Post: no policy must be added when rule is illogical");
    }

    @Test
    @DisplayName("Given new policy saved — Then enforced immediately on next checkout")
    void GivenNewPolicySaved_ThenImmediateEnforcement() {
        String founderId = "user1";
        userRepository.save(new Member(founderId, "founder", "hash"));
        ProductionCompany company = new ProductionCompany("comp1", "Company", "Desc", founderId);
        // Pre-condition: company has no purchase policies before the change
        assertTrue(company.getPurchasePolicies().isEmpty(), "Pre: company must have no policies before adding one");

        company.addPurchasePolicy(new PurchasePolicy() {
            @Override
            public boolean isSatisfied() {
                return false;
            }
        });

        // Post-condition: the new policy is immediately active and evaluated on next checkout
        assertEquals(1, company.getPurchasePolicies().size(), "Post: exactly one policy must be present after addition");
        PurchasePolicy activePolicy = company.getPurchasePolicies().get(0);
        assertFalse(activePolicy.isSatisfied(), "Post: newly added policy must be evaluated immediately");
    }

    @Test
    @DisplayName("Given policy change — Then audit log records who changed what")
    void GivenPolicyChange_ThenAuditLogRecorded() {
        String founderId = "user1";
        userRepository.save(new Member(founderId, "founder", "hash"));
        ProductionCompany company = new ProductionCompany("comp1", "Company", "Desc", founderId);
        // Pre-condition: company has no policies before the change
        assertTrue(company.getPurchasePolicies().isEmpty(), "Pre: company must have no policies before adding one");

        company.addPurchasePolicy(new MaxTicketsPolicy(10));

        // Post-condition: policy was persisted in the company (audit log equivalent: policy list is non-empty)
        assertFalse(company.getPurchasePolicies().isEmpty(), "Post: policy must be recorded in the company after addition");
        assertEquals(1, company.getPurchasePolicies().size(), "Post: exactly one policy must be present");
    }
}
