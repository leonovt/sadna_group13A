package com.sadna.group13a.application.Services;

import com.sadna.group13a.application.DTO.InquiryDTO;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.domain.Aggregates.Company.CompanyStatus;
import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import com.sadna.group13a.domain.Aggregates.Inquiry.Inquiry;
import com.sadna.group13a.domain.Aggregates.Inquiry.InquiryStatus;
import com.sadna.group13a.domain.Aggregates.User.Member;
import com.sadna.group13a.domain.Events.InquiryAnsweredEvent;
import com.sadna.group13a.domain.Interfaces.ICompanyRepository;
import com.sadna.group13a.domain.Interfaces.IInquiryRepository;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InquiryServiceTest {

    @Mock private IInquiryRepository inquiryRepository;
    @Mock private ICompanyRepository companyRepository;
    @Mock private IUserRepository userRepository;
    @Mock private IAuth authGateway;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private SystemLogService systemLogService;

    private InquiryService service;

    private static final String TOKEN = "tok";
    private static final String USER_ID = "u1";
    private static final String COMPANY_ID = "co1";

    @BeforeEach
    void setUp() {
        service = new InquiryService(inquiryRepository, companyRepository, userRepository,
                authGateway, eventPublisher, systemLogService);
    }

    private void activeMember() {
        when(authGateway.validateToken(TOKEN)).thenReturn(true);
        when(authGateway.extractUserId(TOKEN)).thenReturn(USER_ID);
        Member member = mock(Member.class);
        when(member.isActive()).thenReturn(true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(member));
    }

    @Test
    void submitInquiry_success() {
        activeMember();
        ProductionCompany company = mock(ProductionCompany.class);
        when(company.getStatus()).thenReturn(CompanyStatus.ACTIVE);
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));

        Result<String> result = service.submitInquiry(TOKEN, COMPANY_ID, "When do tickets go on sale?");

        assertTrue(result.isSuccess());
        verify(inquiryRepository).save(any(Inquiry.class));
        verify(systemLogService).logEvent(anyString());
    }

    @Test
    void submitInquiry_rejectsInactiveCompany() {
        activeMember();
        ProductionCompany company = mock(ProductionCompany.class);
        when(company.getStatus()).thenReturn(CompanyStatus.INACTIVE);
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));

        assertFalse(service.submitInquiry(TOKEN, COMPANY_ID, "hi").isSuccess());
        verify(inquiryRepository, never()).save(any());
    }

    @Test
    void submitInquiry_rejectsBlankMessage() {
        when(authGateway.validateToken(TOKEN)).thenReturn(true);
        assertFalse(service.submitInquiry(TOKEN, COMPANY_ID, "  ").isSuccess());
        verify(inquiryRepository, never()).save(any());
    }

    @Test
    void respondToInquiry_requiresOwner() {
        when(authGateway.validateToken(TOKEN)).thenReturn(true);
        when(authGateway.extractUserId(TOKEN)).thenReturn(USER_ID);
        Inquiry inquiry = new Inquiry("i1", "buyer1", COMPANY_ID, "msg", LocalDateTime.now());
        when(inquiryRepository.findById("i1")).thenReturn(Optional.of(inquiry));
        ProductionCompany company = mock(ProductionCompany.class);
        when(company.isOwner(USER_ID)).thenReturn(false);
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));

        Result<Void> result = service.respondToInquiry(TOKEN, "i1", "answer");

        assertFalse(result.isSuccess());
        verify(inquiryRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void respondToInquiry_success_answersAndNotifies() {
        when(authGateway.validateToken(TOKEN)).thenReturn(true);
        when(authGateway.extractUserId(TOKEN)).thenReturn(USER_ID);
        Inquiry inquiry = new Inquiry("i1", "buyer1", COMPANY_ID, "msg", LocalDateTime.now());
        when(inquiryRepository.findById("i1")).thenReturn(Optional.of(inquiry));
        ProductionCompany company = mock(ProductionCompany.class);
        when(company.isOwner(USER_ID)).thenReturn(true);
        when(company.getName()).thenReturn("SoundWave");
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));

        Result<Void> result = service.respondToInquiry(TOKEN, "i1", "Tickets open Friday.");

        assertTrue(result.isSuccess());
        assertEquals(InquiryStatus.ANSWERED, inquiry.getStatus());
        verify(inquiryRepository).save(inquiry);

        ArgumentCaptor<InquiryAnsweredEvent> captor = ArgumentCaptor.forClass(InquiryAnsweredEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertEquals("buyer1", captor.getValue().userId());
        assertEquals("SoundWave", captor.getValue().companyName());
    }

    @Test
    void getCompanyInquiries_requiresOwner() {
        when(authGateway.validateToken(TOKEN)).thenReturn(true);
        when(authGateway.extractUserId(TOKEN)).thenReturn(USER_ID);
        ProductionCompany company = mock(ProductionCompany.class);
        when(company.isOwner(USER_ID)).thenReturn(false);
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));

        Result<List<InquiryDTO>> result = service.getCompanyInquiries(TOKEN, COMPANY_ID);
        assertFalse(result.isSuccess());
    }
}
