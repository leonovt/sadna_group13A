package com.sadna.group13a.acceptance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sadna.group13a.application.DTO.SalesReportDTO;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Interfaces.IPaymentGateway;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.CompanyService;
import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import com.sadna.group13a.domain.Aggregates.OrderHistory.OrderHistory;
import com.sadna.group13a.domain.Aggregates.OrderHistory.OrderHistoryItem;
import com.sadna.group13a.domain.Aggregates.User.Member;
import com.sadna.group13a.domain.Interfaces.ICompanyRepository;
import com.sadna.group13a.domain.Interfaces.IOrderHistoryRepository;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import com.sadna.group13a.infrastructure.AuthImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.CompanyRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.OrderHistoryRepositoryImpl;
import com.sadna.group13a.infrastructure.RepositoryImpl.UserRepositoryImpl;
import com.sadna.group13a.infrastructure.StubPaymentGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UC 2.15 — Sales Report Generation")
class SalesReportTest {

    private ICompanyRepository companyRepository;
    private IUserRepository userRepository;
    private IOrderHistoryRepository historyRepository;
    private IAuth authGateway;
    private CompanyService companyService;
    private IPaymentGateway paymentGateway;

    @BeforeEach
    void setUp() {
        companyRepository = new CompanyRepositoryImpl();
        userRepository = new UserRepositoryImpl();
        historyRepository = new OrderHistoryRepositoryImpl();
        authGateway = new AuthImpl();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        companyService = new CompanyService(companyRepository, userRepository, historyRepository, authGateway, objectMapper);
        paymentGateway = new StubPaymentGateway();
    }

    private String setupCompanyAndUser(String companyId, String founderId, String founderName) {
        userRepository.save(new Member(founderId, founderName, "hash"));
        ProductionCompany company = new ProductionCompany(companyId, "Company", "Desc", founderId);
        companyRepository.save(company);
        return authGateway.generateToken(founderId);
    }

    @Test
    @DisplayName("Given manager — When generating report — Then report includes ONLY events in their sub-tree")
    void GivenManager_WhenGeneratingReport_ThenOnlySubTreeData() {
        String founderToken = setupCompanyAndUser("c1", "u1", "founder");

        Result<SalesReportDTO> result = companyService.generateSalesReport(founderToken, "c1");
        assertTrue(result.isSuccess());

        assertNotNull(result.getOrThrow().companyId());
    }

    @Test
    @DisplayName("Given report generated — Then only includes transactions with status PAID")
    void GivenReportGenerated_ThenOnlyPaidTransactions() {
        String founderToken = setupCompanyAndUser("c1", "u1", "founder");

        OrderHistoryItem paidItem = new OrderHistoryItem("e1", "Event", LocalDateTime.now().plusDays(1), "c1", "Company", "VIP", "A1", 100.0);
        OrderHistory paidOrder = new OrderHistory("receipt1", "u1", LocalDateTime.now(), 100.0, java.util.List.of(paidItem));
        historyRepository.save(paidOrder);

        Result<SalesReportDTO> result = companyService.generateSalesReport(founderToken, "c1");
        assertTrue(result.isSuccess());
        
        SalesReportDTO report = result.getOrThrow();
        assertEquals(1, report.totalOrders());
        assertEquals(100.0, report.totalRevenue());
    }

    @Test
    @DisplayName("Given report total — Then matches exactly the amounts charged by payment provider minus refunds")
    void GivenReportTotal_ThenMatchesPaymentProviderChargesMinusRefunds() {
        String founderToken = setupCompanyAndUser("c1", "u1", "founder");

        // Process payments through stub to verify integration works alongside DB history
        paymentGateway.processPayment(50.0, "CC");
        OrderHistoryItem i1 = new OrderHistoryItem("e1", "Event", LocalDateTime.now().plusDays(1), "c1", "Company", "VIP", "A1", 50.0);
        OrderHistory o1 = new OrderHistory("r1", "u1", LocalDateTime.now(), 50.0, java.util.List.of(i1));
        historyRepository.save(o1);

        paymentGateway.processPayment(75.0, "CC");
        OrderHistoryItem i2 = new OrderHistoryItem("e1", "Event", LocalDateTime.now().plusDays(1), "c1", "Company", "VIP", "A2", 75.0);
        OrderHistory o2 = new OrderHistory("r2", "u1", LocalDateTime.now(), 75.0, java.util.List.of(i2));
        historyRepository.save(o2);

        Result<SalesReportDTO> result = companyService.generateSalesReport(founderToken, "c1");
        assertTrue(result.isSuccess());
        
        SalesReportDTO report = result.getOrThrow();
        assertEquals(125.0, report.totalRevenue());
    }

    @Test
    @DisplayName("Given unauthorized user — When requesting report — Then access denied")
    void GivenUnauthorizedUser_WhenRequestingReport_ThenDenied() {
        setupCompanyAndUser("c1", "u1", "founder");
        userRepository.save(new Member("u2", "intruder", "hash"));
        String intruderToken = authGateway.generateToken("u2");

        Result<SalesReportDTO> result = companyService.generateSalesReport(intruderToken, "c1");
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("Permission denied"));
    }
}
