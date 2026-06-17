package com.sadna.group13a.application.Services;

import com.sadna.group13a.application.DTO.ComplaintDTO;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.domain.Aggregates.Admin.Admin;
import com.sadna.group13a.domain.Aggregates.Complaint.Complaint;
import com.sadna.group13a.domain.Aggregates.Complaint.ComplaintStatus;
import com.sadna.group13a.domain.Aggregates.User.Member;
import com.sadna.group13a.domain.Aggregates.User.User;
import com.sadna.group13a.domain.Events.AdminMessageEvent;
import com.sadna.group13a.domain.Interfaces.IAdminRepository;
import com.sadna.group13a.domain.Interfaces.IComplaintRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComplaintServiceTest {

    @Mock private IComplaintRepository complaintRepository;
    @Mock private IUserRepository userRepository;
    @Mock private IAdminRepository adminRepository;
    @Mock private IAuth authGateway;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private SystemLogService systemLogService;

    private ComplaintService service;

    private static final String TOKEN = "tok";
    private static final String USER_ID = "u1";

    @BeforeEach
    void setUp() {
        service = new ComplaintService(complaintRepository, userRepository, adminRepository,
                authGateway, eventPublisher, systemLogService);
    }

    private void memberLoggedIn() {
        when(authGateway.validateToken(TOKEN)).thenReturn(true);
        when(authGateway.extractUserId(TOKEN)).thenReturn(USER_ID);
        Member member = mock(Member.class);
        when(member.isActive()).thenReturn(true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(member));
    }

    @Test
    void submitComplaint_success_savesAndLogs() {
        memberLoggedIn();

        Result<String> result = service.submitComplaint(TOKEN, "Scalping", "Bots bought all tickets");

        assertTrue(result.isSuccess());
        verify(complaintRepository).save(any(Complaint.class));
        verify(systemLogService).logEvent(anyString());
    }

    @Test
    void submitComplaint_rejectsInvalidToken() {
        when(authGateway.validateToken(TOKEN)).thenReturn(false);
        Result<String> result = service.submitComplaint(TOKEN, "s", "m");
        assertFalse(result.isSuccess());
        verify(complaintRepository, never()).save(any());
    }

    @Test
    void submitComplaint_rejectsBlankFields() {
        when(authGateway.validateToken(TOKEN)).thenReturn(true);
        assertFalse(service.submitComplaint(TOKEN, "  ", "msg").isSuccess());
        assertFalse(service.submitComplaint(TOKEN, "subj", " ").isSuccess());
        verify(complaintRepository, never()).save(any());
    }

    @Test
    void submitComplaint_rejectsNonMember() {
        when(authGateway.validateToken(TOKEN)).thenReturn(true);
        when(authGateway.extractUserId(TOKEN)).thenReturn(USER_ID);
        User guest = mock(User.class); // not a Member
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(guest));

        Result<String> result = service.submitComplaint(TOKEN, "s", "m");

        assertFalse(result.isSuccess());
        verify(complaintRepository, never()).save(any());
    }

    @Test
    void respondToComplaint_requiresAdmin() {
        when(authGateway.validateToken(TOKEN)).thenReturn(true);
        when(authGateway.extractUserId(TOKEN)).thenReturn(USER_ID);
        when(adminRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        Result<Void> result = service.respondToComplaint(TOKEN, "c1", "we are investigating");

        assertFalse(result.isSuccess());
        verify(complaintRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void respondToComplaint_success_resolvesAndNotifies() {
        String adminId = "admin1";
        when(authGateway.validateToken(TOKEN)).thenReturn(true);
        when(authGateway.extractUserId(TOKEN)).thenReturn(adminId);
        when(adminRepository.findByUserId(adminId)).thenReturn(Optional.of(mock(Admin.class)));
        User adminUser = mock(User.class);
        when(adminUser.isActive()).thenReturn(true);
        when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));

        Complaint complaint = new Complaint("c1", "buyer1", "Fraud", "fake tickets", LocalDateTime.now());
        when(complaintRepository.findById("c1")).thenReturn(Optional.of(complaint));

        Result<Void> result = service.respondToComplaint(TOKEN, "c1", "Resolved — refund issued.");

        assertTrue(result.isSuccess());
        assertEquals(ComplaintStatus.RESOLVED, complaint.getStatus());
        verify(complaintRepository).save(complaint);

        ArgumentCaptor<AdminMessageEvent> captor = ArgumentCaptor.forClass(AdminMessageEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertEquals("buyer1", captor.getValue().targetUserId());
    }

    @Test
    void respondToComplaint_notFound() {
        String adminId = "admin1";
        when(authGateway.validateToken(TOKEN)).thenReturn(true);
        when(authGateway.extractUserId(TOKEN)).thenReturn(adminId);
        when(adminRepository.findByUserId(adminId)).thenReturn(Optional.of(mock(Admin.class)));
        User adminUser = mock(User.class);
        when(adminUser.isActive()).thenReturn(true);
        when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
        when(complaintRepository.findById("missing")).thenReturn(Optional.empty());

        assertFalse(service.respondToComplaint(TOKEN, "missing", "hi").isSuccess());
    }

    @Test
    void getAllComplaints_requiresAdmin() {
        when(authGateway.validateToken(TOKEN)).thenReturn(true);
        when(authGateway.extractUserId(TOKEN)).thenReturn(USER_ID);
        when(adminRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        Result<List<ComplaintDTO>> result = service.getAllComplaints(TOKEN);
        assertFalse(result.isSuccess());
    }
}
