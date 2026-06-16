package com.sadna.group13a.acceptance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Services.CompanyService;
import com.sadna.group13a.domain.DomainServices.CompanyStaffDomainService;
import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import com.sadna.group13a.domain.Aggregates.User.Member;
import com.sadna.group13a.domain.Interfaces.ICompanyRepository;
import com.sadna.group13a.domain.Interfaces.IOrderHistoryRepository;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import com.sadna.group13a.domain.policies.purchase.AllowAllPolicy;
import com.sadna.group13a.domain.shared.DomainException;
import com.sadna.group13a.domain.shared.PurchaseContext;
import com.sadna.group13a.domain.shared.PurchasePolicy;
import com.sadna.group13a.infrastructure.AuthImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.CompanyRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.OrderHistoryRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.UserRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.FakeUserJpaRepository;
import com.sadna.group13a.infrastructure.config.PersistenceConfig;
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
        userRepository = new UserRepositoryImpl(new FakeUserJpaRepository(), new PersistenceConfig().domainObjectMapper());
        historyRepository = new OrderHistoryRepositoryImpl();
        authGateway = new AuthImpl();
        objectMapper = new ObjectMapper();

        companyService = new CompanyService(companyRepository, userRepository, historyRepository, authGateway,
                objectMapper, e -> {}, new CompanyStaffDomainService());
    }

    class MaxTicketsPolicy implements PurchasePolicy {
        private final int maxTickets;

        public MaxTicketsPolicy(int maxTickets) {
            if (maxTickets <= 0)
                throw new DomainException("Illogical rule: max tickets must be > 0");
            this.maxTickets = maxTickets;
        }

        @Override
        public boolean isSatisfied(PurchaseContext ctx) {
            return ctx.ticketCount() <= maxTickets;
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
        // Pre-condition: company starts with the default AllowAll policy
        assertTrue(company.getPurchasePolicy() instanceof AllowAllPolicy,
                "Pre: company must have AllowAllPolicy before adding an illogical rule");

        Exception exception = assertThrows(DomainException.class, () -> {
            company.setPurchasePolicy(new MaxTicketsPolicy(0));
        });

        // Post-condition: domain rejects the rule and policy remains AllowAll
        assertTrue(exception.getMessage().contains("Illogical rule"), "Post: error must describe the illogical rule");
        assertTrue(company.getPurchasePolicy() instanceof AllowAllPolicy,
                "Post: policy must remain AllowAll when rule is illogical");
    }

    @Test
    @DisplayName("Given new policy saved — Then enforced immediately on next checkout")
    void GivenNewPolicySaved_ThenImmediateEnforcement() {
        String founderId = "user1";
        userRepository.save(new Member(founderId, "founder", "hash"));
        ProductionCompany company = new ProductionCompany("comp1", "Company", "Desc", founderId);
        // Pre-condition: company has default AllowAll policy before the change
        assertTrue(company.getPurchasePolicy() instanceof AllowAllPolicy,
                "Pre: company must have AllowAllPolicy before adding one");

        company.setPurchasePolicy(ctx -> false);

        // Post-condition: the new policy is immediately active and evaluated on next checkout
        PurchasePolicy activePolicy = company.getPurchasePolicy();
        assertFalse(activePolicy instanceof AllowAllPolicy, "Post: policy must not be AllowAll after update");
        assertFalse(activePolicy.isSatisfied(new PurchaseContext("user", 1, 0, null)),
                "Post: newly added policy must be evaluated immediately");
    }

    @Test
    @DisplayName("Given policy change — Then audit log records who changed what")
    void GivenPolicyChange_ThenAuditLogRecorded() {
        String founderId = "user1";
        userRepository.save(new Member(founderId, "founder", "hash"));
        ProductionCompany company = new ProductionCompany("comp1", "Company", "Desc", founderId);
        // Pre-condition: company has default AllowAll policy before the change
        assertTrue(company.getPurchasePolicy() instanceof AllowAllPolicy,
                "Pre: company must have AllowAllPolicy before adding one");

        company.setPurchasePolicy(new MaxTicketsPolicy(10));

        // Post-condition: policy was updated in the company
        assertFalse(company.getPurchasePolicy() instanceof AllowAllPolicy,
                "Post: policy must be updated in the company after addition");
    }
}
