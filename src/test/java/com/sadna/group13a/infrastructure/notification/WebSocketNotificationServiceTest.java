package com.sadna.group13a.infrastructure.notification;

import com.sadna.group13a.domain.Interfaces.ICompanyRepository;
import com.sadna.group13a.domain.Interfaces.IEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("WebSocketNotificationService")
class WebSocketNotificationServiceTest {

    private NotificationBroadcaster broadcaster;
    private IEventRepository eventRepository;
    private ICompanyRepository companyRepository;
    private WebSocketNotificationService service;

    @BeforeEach
    void setUp() {
        broadcaster = mock(NotificationBroadcaster.class);
        eventRepository = mock(IEventRepository.class);
        companyRepository = mock(ICompanyRepository.class);
        when(eventRepository.findById(anyString())).thenReturn(Optional.empty());
        when(companyRepository.findById(anyString())).thenReturn(Optional.empty());
        service = new WebSocketNotificationService(broadcaster, eventRepository, companyRepository);
    }

    @Test
    @DisplayName("notifyQueueTurnArrived — sends to the correct user")
    void notifyQueueTurnArrived_sendsToUser() {
        service.notifyQueueTurnArrived("u1", "e1", LocalDateTime.now());
        verify(broadcaster).send(eq("u1"), anyString());
    }

    @Test
    @DisplayName("notifyOrderCompleted — sends to the correct user")
    void notifyOrderCompleted_sendsToUser() {
        service.notifyOrderCompleted("u1", "r1", 99.0);
        verify(broadcaster).send(eq("u1"), anyString());
    }

    @Test
    @DisplayName("notifyUserBanned — sends to the correct user")
    void notifyUserBanned_sendsToUser() {
        service.notifyUserBanned("u1", "admin1");
        verify(broadcaster).send(eq("u1"), anyString());
    }

    @Test
    @DisplayName("notifyUserSuspended — sends to the correct user")
    void notifyUserSuspended_sendsToUser() {
        service.notifyUserSuspended("u1", LocalDateTime.now().plusDays(7));
        verify(broadcaster).send(eq("u1"), anyString());
    }

    @Test
    @DisplayName("notifyCompanyClosed — sends to each staff member")
    void notifyCompanyClosed_sendsToAllStaff() {
        service.notifyCompanyClosed(List.of("s1", "s2"), "c1", "admin1");
        verify(broadcaster).send(eq("s1"), anyString());
        verify(broadcaster).send(eq("s2"), anyString());
    }

    @Test
    @DisplayName("notifyRaffleDrawn — sends to each loser")
    void notifyRaffleDrawn_sendsToLosers() {
        service.notifyRaffleDrawn(List.of("loser1", "loser2"), "e1", 3);
        verify(broadcaster).send(eq("loser1"), anyString());
        verify(broadcaster).send(eq("loser2"), anyString());
    }

    @Test
    @DisplayName("notifyActionFailed — sends to the correct user")
    void notifyActionFailed_sendsToUser() {
        service.notifyActionFailed("u1", "payment failed");
        verify(broadcaster).send(eq("u1"), anyString());
    }

    @Test
    @DisplayName("notifyCompanySuspended — sends to each staff member")
    void notifyCompanySuspended_sendsToAllStaff() {
        service.notifyCompanySuspended(List.of("s1", "s2"), "c1");
        verify(broadcaster).send(eq("s1"), anyString());
        verify(broadcaster).send(eq("s2"), anyString());
    }

    @Test
    @DisplayName("notifyCompanyReopened — sends to each staff member")
    void notifyCompanyReopened_sendsToAllStaff() {
        service.notifyCompanyReopened(List.of("s1"), "c1");
        verify(broadcaster).send(eq("s1"), anyString());
    }

    @Test
    @DisplayName("notifyStaffNominated — sends nomination (with companyId) to the correct user")
    void notifyStaffNominated_sendsToUser() {
        service.notifyStaffNominated("u1", "c1", "MANAGER");
        verify(broadcaster).sendNomination(eq("u1"), anyString(), eq("c1"));
    }

    @Test
    @DisplayName("notifyStaffRemoved — sends to the correct user")
    void notifyStaffRemoved_sendsToUser() {
        service.notifyStaffRemoved("u1", "c1");
        verify(broadcaster).send(eq("u1"), anyString());
    }

    @Test
    @DisplayName("notifyPermissionsUpdated — sends to the correct user")
    void notifyPermissionsUpdated_sendsToUser() {
        service.notifyPermissionsUpdated("u1", "c1");
        verify(broadcaster).send(eq("u1"), anyString());
    }

    @Test
    @DisplayName("notifyCartExpired — sends to the correct user")
    void notifyCartExpired_sendsToUser() {
        service.notifyCartExpired("u1");
        verify(broadcaster).send(eq("u1"), anyString());
    }

    @Test
    @DisplayName("notifyEventCancelled — sends to each buyer")
    void notifyEventCancelled_sendsToAllBuyers() {
        service.notifyEventCancelled(List.of("b1", "b2"), "e1", "Concert");
        verify(broadcaster).send(eq("b1"), anyString());
        verify(broadcaster).send(eq("b2"), anyString());
    }

    @Test
    @DisplayName("notifyRefundIssued — sends to the correct user")
    void notifyRefundIssued_sendsToUser() {
        service.notifyRefundIssued("u1", "r1", 50.0, "Concert");
        verify(broadcaster).send(eq("u1"), anyString());
    }

    @Test
    @DisplayName("notifyEventRescheduled — sends to each buyer")
    void notifyEventRescheduled_sendsToAllBuyers() {
        service.notifyEventRescheduled(List.of("b1"), "e1", "Concert", LocalDateTime.now().plusDays(1));
        verify(broadcaster).send(eq("b1"), anyString());
    }

    @Test
    @DisplayName("notifyUserReactivated — sends to the correct user")
    void notifyUserReactivated_sendsToUser() {
        service.notifyUserReactivated("u1");
        verify(broadcaster).send(eq("u1"), anyString());
    }

    @Test
    @DisplayName("notifyEventSoldOut — sends to each staff member")
    void notifyEventSoldOut_sendsToAllStaff() {
        service.notifyEventSoldOut(List.of("s1", "s2"), "e1", "Concert");
        verify(broadcaster).send(eq("s1"), anyString());
        verify(broadcaster).send(eq("s2"), anyString());
    }

    @Test
    @DisplayName("notifyRaffleWon — sends to the correct user")
    void notifyRaffleWon_sendsToUser() {
        service.notifyRaffleWon("u1", "e1", "AUTH-123", LocalDateTime.now().plusHours(1));
        verify(broadcaster).send(eq("u1"), anyString());
    }

    @Test
    @DisplayName("notifyAdminMessage — sends to the correct user")
    void notifyAdminMessage_sendsToUser() {
        service.notifyAdminMessage("u1", "Welcome back!");
        verify(broadcaster).send(eq("u1"), anyString());
    }
}
