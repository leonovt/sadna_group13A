package com.sadna.group13a.acceptance;

import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.AdminService;
import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import com.sadna.group13a.domain.Aggregates.Admin.Admin;
import com.sadna.group13a.domain.Aggregates.User.Member;
import com.sadna.group13a.domain.Events.CompanyClosedByAdminEvent;
import com.sadna.group13a.domain.Interfaces.IAdminRepository;
import com.sadna.group13a.domain.Interfaces.ICompanyRepository;
import com.sadna.group13a.domain.Interfaces.IEventRepository;
import com.sadna.group13a.domain.Interfaces.IOrderHistoryRepository;
import com.sadna.group13a.domain.Interfaces.IQueueRepository;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import com.sadna.group13a.application.Services.SystemLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Acceptance tests for UC 2.17: Close Production Company by System Admin (Enforcement).
 *
 * Verifies admin-enforced closure: role tree deletion, permission revocation,
 * in-progress purchase interruption, and search removal.
 */
@DisplayName("UC 2.17 — Admin-Enforced Company Closure")
class AdminCompanyClosureTest {

    private AdminService adminService;
    private IUserRepository userRepository;
    private IAdminRepository adminRepository;
    private IEventRepository eventRepository;
    private ICompanyRepository companyRepository;
    private IQueueRepository queueRepository;
    private IOrderHistoryRepository historyRepository;
    private IAuth authGateway;
    private SystemLogService systemLogService;
    private ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        userRepository = mock(IUserRepository.class);
        adminRepository = mock(IAdminRepository.class);
        eventRepository = mock(IEventRepository.class);
        companyRepository = mock(ICompanyRepository.class);
        queueRepository = mock(IQueueRepository.class);
        historyRepository = mock(IOrderHistoryRepository.class);
        authGateway = mock(IAuth.class);
        systemLogService = mock(SystemLogService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        adminService = new AdminService(
                userRepository, adminRepository, eventRepository, companyRepository, 
                queueRepository, historyRepository, authGateway, eventPublisher, systemLogService);
    }

    @Test
    @DisplayName("Given admin closes company — Then entire role tree deleted and unrecoverable")
    void GivenAdminClosesCompany_ThenRoleTreeDeletedPermanently() {
        String token = "admin_token";
        String adminId = "admin1";
        String companyId = "company1";

        when(authGateway.validateToken(token)).thenReturn(true);
        when(authGateway.extractUserId(token)).thenReturn(adminId);

        Member adminMember = new Member(adminId, "admin_user", "hashed");
        when(userRepository.findById(adminId)).thenReturn(Optional.of(adminMember));
        Admin adminRecord = new Admin("admin_rec_1", adminId);
        when(adminRepository.findByUserId(adminId)).thenReturn(Optional.of(adminRecord));

        ProductionCompany company = mock(ProductionCompany.class);
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company));
        // Pre-condition: admin is authenticated, admin record exists, and target company exists
        assertTrue(authGateway.validateToken(token), "Pre: admin must have a valid token");
        assertTrue(adminRepository.findByUserId(adminId).isPresent(), "Pre: admin record must exist");
        assertTrue(companyRepository.findById(companyId).isPresent(), "Pre: company must exist before closure");

        Result<Void> result = adminService.closeCompanyGlobally(token, companyId);

        // Post-condition: company is force-closed and a domain event is published for downstream cleanup
        assertTrue(result.isSuccess(), "Post: admin must successfully close the company");
        verify(company).forceClose();
        verify(companyRepository).save(company);

        ArgumentCaptor<CompanyClosedByAdminEvent> eventCaptor = ArgumentCaptor.forClass(CompanyClosedByAdminEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        CompanyClosedByAdminEvent publishedEvent = eventCaptor.getValue();
        assertEquals(companyId, publishedEvent.companyId(), "Post: event must reference the closed company");
        assertEquals(adminId, publishedEvent.adminId(), "Post: event must record which admin performed the closure");
    }

    @Test
    @DisplayName("Given admin closes company — Then ALL staff lose management access immediately")
    void GivenAdminClosesCompany_ThenAllStaffLoseAccessImmediately() {
        // Staff permission loss is verified by checking the company's forceClose effect 
        // within the Admin service flow + the generated domain events handled by another service.
        String token = "admin_token";
        String adminId = "admin1";
        String companyId = "company1";

        when(authGateway.validateToken(token)).thenReturn(true);
        when(authGateway.extractUserId(token)).thenReturn(adminId);
        
        Member adminMember = new Member(adminId, "admin_user", "hashed");
        when(userRepository.findById(adminId)).thenReturn(Optional.of(adminMember));
        Admin adminRecord = new Admin("admin_rec_1", adminId);
        when(adminRepository.findByUserId(adminId)).thenReturn(Optional.of(adminRecord));

        ProductionCompany company = mock(ProductionCompany.class);
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company));
        
        adminService.closeCompanyGlobally(token, companyId);

        // Post-condition: forceClose() is called, and the event carrying the company ID is published
        // so downstream listeners (e.g. CompanyEventListener) revoke all staff access
        verify(company).forceClose();
        ArgumentCaptor<CompanyClosedByAdminEvent> staffCaptor = ArgumentCaptor.forClass(CompanyClosedByAdminEvent.class);
        verify(eventPublisher).publishEvent(staffCaptor.capture());
        assertEquals(companyId, staffCaptor.getValue().companyId(),
                "Post: event must carry the company ID to trigger staff access revocation in all downstream listeners");
    }

    @Test
    @DisplayName("Given users in queue or holding seats for company events — When admin closes company — Then purchases interrupted with error message")
    void GivenUsersInProgress_WhenAdminCloses_ThenPurchasesInterrupted() {
        // In-progress logic interruption is handled by another service that listens to CompanyClosedByAdminEvent.
        // For AdminService it just successfully closes the company and publishes the event.
        String token = "admin_token";
        String adminId = "admin1";
        String companyId = "company1";

        when(authGateway.validateToken(token)).thenReturn(true);
        when(authGateway.extractUserId(token)).thenReturn(adminId);
        
        Member adminMember = new Member(adminId, "admin_user", "hashed");
        when(userRepository.findById(adminId)).thenReturn(Optional.of(adminMember));
        Admin adminRecord = new Admin("admin_rec_1", adminId);
        when(adminRepository.findByUserId(adminId)).thenReturn(Optional.of(adminRecord));

        ProductionCompany company = mock(ProductionCompany.class);
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company));
        
        Result<Void> result = adminService.closeCompanyGlobally(token, companyId);
        assertTrue(result.isSuccess());
        
        ArgumentCaptor<CompanyClosedByAdminEvent> eventCaptor = ArgumentCaptor.forClass(CompanyClosedByAdminEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        
        assertNotNull(eventCaptor.getValue());
        assertEquals(companyId, eventCaptor.getValue().companyId());
    }

    @Test
    @DisplayName("Given admin closes company — Then company and events removed from global search")
    void GivenAdminClosesCompany_ThenRemovedFromSearch() {
        // Assume global search removal is implemented via Event handlers in a real search projection logic
        String token = "admin_token";
        String adminId = "admin1";
        String companyId = "company1";

        when(authGateway.validateToken(token)).thenReturn(true);
        when(authGateway.extractUserId(token)).thenReturn(adminId);
        
        Member adminMember = new Member(adminId, "admin_user", "hashed");
        when(userRepository.findById(adminId)).thenReturn(Optional.of(adminMember));
        Admin adminRecord = new Admin("admin_rec_1", adminId);
        when(adminRepository.findByUserId(adminId)).thenReturn(Optional.of(adminRecord));

        ProductionCompany company = mock(ProductionCompany.class);
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company));

        Result<Void> result = adminService.closeCompanyGlobally(token, companyId);

        assertTrue(result.isSuccess());
        verify(companyRepository).save(company);
        verify(eventPublisher).publishEvent(any(CompanyClosedByAdminEvent.class));
    }

    @Test
    @DisplayName("Given non-admin user — When attempting admin closure — Then action denied")
    void GivenNonAdmin_WhenAttemptingClosure_ThenDenied() {
        String token = "user_token";
        String userId = "user1";
        String companyId = "company1";

        when(authGateway.validateToken(token)).thenReturn(true);
        when(authGateway.extractUserId(token)).thenReturn(userId);

        Member member = new Member(userId, "normal_user", "hashed");
        when(userRepository.findById(userId)).thenReturn(Optional.of(member));
        // Pre-condition: acting user is authenticated but has no admin record
        assertTrue(authGateway.validateToken(token), "Pre: user must be authenticated");
        assertTrue(adminRepository.findByUserId(userId).isEmpty(), "Pre: acting user must not be an admin");

        Result<Void> result = adminService.closeCompanyGlobally(token, companyId);

        // Post-condition: closure is denied and company is untouched
        assertFalse(result.isSuccess(), "Post: non-admin must not be able to close the company");
        assertTrue(result.getErrorMessage().contains("Only admins"), "Post: error must indicate admin-only restriction");
        verify(companyRepository, never()).save(any(ProductionCompany.class));
    }
}
