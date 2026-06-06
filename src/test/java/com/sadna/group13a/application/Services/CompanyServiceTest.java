package com.sadna.group13a.application.Services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sadna.group13a.application.DTO.CompanyDTO;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.domain.Aggregates.Company.CompanyPermission;
import com.sadna.group13a.domain.DomainServices.CompanyStaffDomainService;
import com.sadna.group13a.domain.Aggregates.Company.CompanyRole;
import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import com.sadna.group13a.domain.Aggregates.User.Member;
import com.sadna.group13a.domain.Interfaces.ICompanyRepository;
import com.sadna.group13a.domain.Interfaces.IOrderHistoryRepository;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompanyServiceTest {

    @Mock private ICompanyRepository companyRepository;
    @Mock private IUserRepository userRepository;
    @Mock private IOrderHistoryRepository historyRepository;
    @Mock private IAuth authGateway;
    @Mock private ObjectMapper objectMapper;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Spy  private CompanyStaffDomainService companyStaffDomainService = new CompanyStaffDomainService();

    @InjectMocks
    private CompanyService companyService;

    private static final String TOKEN      = "valid-token";
    private static final String FOUNDER_ID = "founder-1";
    private static final String COMPANY_ID = "co-1";

    private ProductionCompany company;
    private Member founder;

    @BeforeEach
    void setUp() {
        founder = new Member(FOUNDER_ID, "alice", "hash");
        company = new ProductionCompany(COMPANY_ID, "Acme", "Desc", FOUNDER_ID);

        lenient().when(authGateway.validateToken(TOKEN)).thenReturn(true);
        lenient().when(authGateway.extractUserId(TOKEN)).thenReturn(FOUNDER_ID);
        lenient().when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
    }

    // ── createCompany ─────────────────────────────────────────────

    @Test
    void givenInvalidToken_whenCreateCompany_thenReturnsFailure() {
        when(authGateway.validateToken("bad")).thenReturn(false);

        assertFalse(companyService.createCompany("bad", "Acme", "Desc").isSuccess());
        verify(companyRepository, never()).save(any());
    }

    @Test
    void givenFounderNotFound_whenCreateCompany_thenReturnsFailure() {
        when(userRepository.findById(FOUNDER_ID)).thenReturn(Optional.empty());

        assertFalse(companyService.createCompany(TOKEN, "Acme", "Desc").isSuccess());
        verify(companyRepository, never()).save(any());
    }

    @Test
    void givenValidRequest_whenCreateCompany_thenCompanySaved() {
        when(userRepository.findById(FOUNDER_ID)).thenReturn(Optional.of(founder));

        Result<Boolean> result = companyService.createCompany(TOKEN, "Acme", "Events");

        assertTrue(result.isSuccess());
        verify(companyRepository).save(any(ProductionCompany.class));
    }

    // ── appointManager ────────────────────────────────────────────

    @Test
    void givenInvalidToken_whenAppointManager_thenReturnsFailure() {
        when(authGateway.validateToken("bad")).thenReturn(false);

        assertFalse(companyService.appointManager("bad", COMPANY_ID, "bob", null).isSuccess());
    }

    @Test
    void givenCompanyNotFound_whenAppointManager_thenReturnsFailure() {
        when(companyRepository.findById("missing")).thenReturn(Optional.empty());

        assertFalse(companyService.appointManager(TOKEN, "missing", "bob", null).isSuccess());
    }

    @Test
    void givenTargetNotFound_whenAppointManager_thenReturnsFailure() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertFalse(companyService.appointManager(TOKEN, COMPANY_ID, "ghost", null).isSuccess());
    }

    @Test
    void givenFounderAppointingNewManager_whenAppointManager_thenPendingNominationCreated() {
        Member target = new Member("target-1", "bob", "hash");
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(target));

        Result<Void> result = companyService.appointManager(TOKEN, COMPANY_ID, "bob", null);

        assertTrue(result.isSuccess());
        // Nomination is pending — user is not yet a manager until they accept
        assertFalse(company.isManager("target-1"));
        assertTrue(company.getPendingAppointments().containsKey("target-1"));
        verify(companyRepository).save(company);
    }

    // ── suspendCompany ────────────────────────────────────────────

    @Test
    void givenFounder_whenSuspendCompany_thenCompanySuspendedAndSaved() {
        Result<Void> result = companyService.suspendCompany(TOKEN, COMPANY_ID);

        assertTrue(result.isSuccess());
        verify(companyRepository).save(company);
    }

    @Test
    void givenNonFounder_whenSuspendCompany_thenReturnsFailure() {
        when(authGateway.extractUserId(TOKEN)).thenReturn("outsider");

        assertFalse(companyService.suspendCompany(TOKEN, COMPANY_ID).isSuccess());
    }

    // ── reopenCompany ─────────────────────────────────────────────

    @Test
    void givenSuspendedCompany_whenReopenByFounder_thenCompanyReopened() {
        company.suspendCompany(FOUNDER_ID);

        Result<Void> result = companyService.reopenCompany(TOKEN, COMPANY_ID);

        assertTrue(result.isSuccess());
        verify(companyRepository).save(company);
    }

    // ── resign ────────────────────────────────────────────────────

    @Test
    void givenManager_whenResign_thenRemovedFromStaffAndCompanySaved() {
        company.nominateStaff(FOUNDER_ID, "mgr-1", CompanyRole.MANAGER, null);
        company.acceptNomination("mgr-1");

        when(authGateway.extractUserId(TOKEN)).thenReturn("mgr-1");

        Result<Void> result = companyService.resign(TOKEN, COMPANY_ID);

        assertTrue(result.isSuccess());
        assertFalse(company.getStaff().containsKey("mgr-1"));
        verify(companyRepository).save(company);
    }

    @Test
    void givenFounder_whenResign_thenReturnsFailure() {
        // Founder cannot resign — domain enforces this
        Result<Void> result = companyService.resign(TOKEN, COMPANY_ID);

        assertFalse(result.isSuccess());
    }

    // ── getCompany ────────────────────────────────────────────────

    @Test
    void givenInvalidToken_whenGetCompany_thenReturnsFailure() {
        when(authGateway.validateToken("bad")).thenReturn(false);

        assertFalse(companyService.getCompany("bad", COMPANY_ID).isSuccess());
    }

    @Test
    void givenCompanyNotFound_whenGetCompany_thenReturnsFailure() {
        when(companyRepository.findById("missing")).thenReturn(Optional.empty());

        assertFalse(companyService.getCompany(TOKEN, "missing").isSuccess());
    }

    @Test
    void givenExistingCompany_whenGetCompany_thenReturnsDto() {
        Result<CompanyDTO> result = companyService.getCompany(TOKEN, COMPANY_ID);

        assertTrue(result.isSuccess());
        assertEquals("co-1", result.getData().get().id());
    }

    // ── updatePermissions ─────────────────────────────────────────

    @Test
    void givenFounderUpdatingManagerPermissions_whenUpdatePermissions_thenCompanySaved() {
        company.nominateStaff(FOUNDER_ID, "mgr-1", CompanyRole.MANAGER, null);
        company.acceptNomination("mgr-1");
        Member mgr = new Member("mgr-1", "bob", "hash");
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(mgr));

        Result<Void> result = companyService.updatePermissions(
                TOKEN, COMPANY_ID, "bob", Set.of(CompanyPermission.VIEW_REPORTS));

        assertTrue(result.isSuccess());
        assertTrue(company.hasPermission("mgr-1", CompanyPermission.VIEW_REPORTS));
        verify(companyRepository).save(company);
    }

    @Test
    void givenTargetNotFound_whenUpdatePermissions_thenReturnsFailure() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertFalse(companyService.updatePermissions(TOKEN, COMPANY_ID, "ghost", Set.of()).isSuccess());
    }

    // ── getPurchasePolicyDescription ──────────────────────────────

    @Test
    void givenOwner_whenGetPurchasePolicyDescription_thenReturnsDescription() {
        Result<String> result = companyService.getPurchasePolicyDescription(TOKEN, COMPANY_ID);
        assertTrue(result.isSuccess());
        assertNotNull(result.getData().get());
    }

    @Test
    void givenInvalidToken_whenGetPurchasePolicyDescription_thenReturnsFailure() {
        when(authGateway.validateToken("bad")).thenReturn(false);
        assertFalse(companyService.getPurchasePolicyDescription("bad", COMPANY_ID).isSuccess());
    }

    @Test
    void givenUnknownCompany_whenGetPurchasePolicyDescription_thenReturnsFailure() {
        assertFalse(companyService.getPurchasePolicyDescription(TOKEN, "unknown").isSuccess());
    }

    // ── getDiscountPolicyDescription ──────────────────────────────

    @Test
    void givenOwner_whenGetDiscountPolicyDescription_thenReturnsDescription() {
        Result<String> result = companyService.getDiscountPolicyDescription(TOKEN, COMPANY_ID);
        assertTrue(result.isSuccess());
        assertNotNull(result.getData().get());
    }

    @Test
    void givenInvalidToken_whenGetDiscountPolicyDescription_thenReturnsFailure() {
        when(authGateway.validateToken("bad")).thenReturn(false);
        assertFalse(companyService.getDiscountPolicyDescription("bad", COMPANY_ID).isSuccess());
    }
}
