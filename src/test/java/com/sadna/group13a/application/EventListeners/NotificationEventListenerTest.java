package com.sadna.group13a.application.EventListeners;

import com.sadna.group13a.application.Interfaces.INotificationService;
import com.sadna.group13a.domain.Aggregates.Company.CompanyRole;
import com.sadna.group13a.domain.Events.AdminMessageEvent;
import com.sadna.group13a.domain.Events.CartExpiredEvent;
import com.sadna.group13a.domain.Events.CheckoutFailedEvent;
import com.sadna.group13a.domain.Events.CompanyReopenedEvent;
import com.sadna.group13a.domain.Events.CompanySuspendedEvent;
import com.sadna.group13a.domain.Events.EventCancelledEvent;
import com.sadna.group13a.domain.Events.EventRescheduledEvent;
import com.sadna.group13a.domain.Events.EventSoldOutEvent;
import com.sadna.group13a.domain.Events.PermissionsUpdatedEvent;
import com.sadna.group13a.domain.Events.RaffleWonEvent;
import com.sadna.group13a.domain.Events.RefundIssuedEvent;
import com.sadna.group13a.domain.Events.StaffNominatedEvent;
import com.sadna.group13a.domain.Events.StaffRemovedEvent;
import com.sadna.group13a.domain.Events.UserReactivatedEvent;
import com.sadna.group13a.domain.Events.UserSuspendedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;

@DisplayName("NotificationEventListener — remaining event handlers")
class NotificationEventListenerTest {

    private INotificationService notificationService;
    private NotificationEventListener listener;

    @BeforeEach
    void setUp() {
        notificationService = mock(INotificationService.class);
        listener = new NotificationEventListener(notificationService);
    }

    @Test
    @DisplayName("onUserSuspended — delegates to notifyUserSuspended with correct args")
    void onUserSuspended_delegates() {
        LocalDateTime until = LocalDateTime.now().plusDays(3);
        listener.onUserSuspended(new UserSuspendedEvent("u1", "admin1", until));
        verify(notificationService).notifyUserSuspended("u1", until);
    }

    @Test
    @DisplayName("onCheckoutFailed — delegates to notifyActionFailed")
    void onCheckoutFailed_delegates() {
        listener.onCheckoutFailed(new CheckoutFailedEvent("u1", "insufficient funds"));
        verify(notificationService).notifyActionFailed("u1", "insufficient funds");
    }

    @Test
    @DisplayName("onCompanySuspended — delegates to notifyCompanySuspended with staff list")
    void onCompanySuspended_delegates() {
        listener.onCompanySuspended(new CompanySuspendedEvent("c1", "admin1", List.of("s1", "s2")));
        verify(notificationService).notifyCompanySuspended(List.of("s1", "s2"), "c1");
    }

    @Test
    @DisplayName("onCompanyReopened — delegates to notifyCompanyReopened with staff list")
    void onCompanyReopened_delegates() {
        listener.onCompanyReopened(new CompanyReopenedEvent("c1", "admin1", List.of("s1")));
        verify(notificationService).notifyCompanyReopened(List.of("s1"), "c1");
    }

    @Test
    @DisplayName("onStaffNominated — delegates with role name string")
    void onStaffNominated_delegates() {
        listener.onStaffNominated(new StaffNominatedEvent("u1", "c1", CompanyRole.MANAGER, "admin1"));
        verify(notificationService).notifyStaffNominated("u1", "c1", "MANAGER");
    }

    @Test
    @DisplayName("onStaffRemoved — delegates notifyStaffRemoved for each removed user")
    void onStaffRemoved_delegatesForEachUser() {
        listener.onStaffRemoved(new StaffRemovedEvent(List.of("u1", "u2"), "c1", "admin1"));
        verify(notificationService).notifyStaffRemoved("u1", "c1");
        verify(notificationService).notifyStaffRemoved("u2", "c1");
    }

    @Test
    @DisplayName("onPermissionsUpdated — delegates to notifyPermissionsUpdated")
    void onPermissionsUpdated_delegates() {
        listener.onPermissionsUpdated(new PermissionsUpdatedEvent("u1", "c1"));
        verify(notificationService).notifyPermissionsUpdated("u1", "c1");
    }

    @Test
    @DisplayName("onCartExpired — delegates to notifyCartExpired")
    void onCartExpired_delegates() {
        listener.onCartExpired(new CartExpiredEvent("u1", "cart-1"));
        verify(notificationService).notifyCartExpired("u1");
    }

    @Test
    @DisplayName("onEventCancelled — delegates to notifyEventCancelled with buyer list")
    void onEventCancelled_delegates() {
        listener.onEventCancelled(new EventCancelledEvent("e1", "Concert", List.of("b1", "b2")));
        verify(notificationService).notifyEventCancelled(List.of("b1", "b2"), "e1", "Concert");
    }

    @Test
    @DisplayName("onRefundIssued — delegates to notifyRefundIssued with correct args")
    void onRefundIssued_delegates() {
        listener.onRefundIssued(new RefundIssuedEvent("u1", "r1", 50.0, "Concert"));
        verify(notificationService).notifyRefundIssued("u1", "r1", 50.0, "Concert");
    }

    @Test
    @DisplayName("onEventRescheduled — delegates to notifyEventRescheduled with buyer list and new date")
    void onEventRescheduled_delegates() {
        LocalDateTime newDate = LocalDateTime.now().plusDays(5);
        listener.onEventRescheduled(new EventRescheduledEvent("e1", "Concert", newDate, List.of("b1")));
        verify(notificationService).notifyEventRescheduled(List.of("b1"), "e1", "Concert", newDate);
    }

    @Test
    @DisplayName("onUserReactivated — delegates to notifyUserReactivated")
    void onUserReactivated_delegates() {
        listener.onUserReactivated(new UserReactivatedEvent("u1", "admin1"));
        verify(notificationService).notifyUserReactivated("u1");
    }

    @Test
    @DisplayName("onEventSoldOut — delegates to notifyEventSoldOut with staff list")
    void onEventSoldOut_delegates() {
        listener.onEventSoldOut(new EventSoldOutEvent("e1", "Concert", List.of("s1", "s2")));
        verify(notificationService).notifyEventSoldOut(List.of("s1", "s2"), "e1", "Concert");
    }

    @Test
    @DisplayName("onRaffleWon — delegates to notifyRaffleWon with auth code and expiry")
    void onRaffleWon_delegates() {
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(2);
        listener.onRaffleWon(new RaffleWonEvent("u1", "e1", "AUTH-XYZ", expiresAt));
        verify(notificationService).notifyRaffleWon("u1", "e1", "AUTH-XYZ", expiresAt);
    }

    @Test
    @DisplayName("onAdminMessage — delegates to notifyAdminMessage")
    void onAdminMessage_delegates() {
        listener.onAdminMessage(new AdminMessageEvent("u1", "admin1", "Hello user!"));
        verify(notificationService).notifyAdminMessage("u1", "Hello user!");
    }
}
