package com.sadna.group13a.application.EventListeners;

import com.sadna.group13a.application.Interfaces.INotificationService;
import com.sadna.group13a.domain.Events.AdminMessageEvent;
import com.sadna.group13a.domain.Events.CartExpiredEvent;
import com.sadna.group13a.domain.Events.CheckoutFailedEvent;
import com.sadna.group13a.domain.Events.CompanyClosedByAdminEvent;
import com.sadna.group13a.domain.Events.CompanyReopenedEvent;
import com.sadna.group13a.domain.Events.CompanySuspendedEvent;
import com.sadna.group13a.domain.Events.EventCancelledEvent;
import com.sadna.group13a.domain.Events.EventRescheduledEvent;
import com.sadna.group13a.domain.Events.EventSoldOutEvent;
import com.sadna.group13a.domain.Events.OrderCompletedEvent;
import com.sadna.group13a.domain.Events.PermissionsUpdatedEvent;
import com.sadna.group13a.domain.Events.QueueTurnArrivedEvent;
import com.sadna.group13a.domain.Events.RefundIssuedEvent;
import com.sadna.group13a.domain.Events.RaffleDrawnEvent;
import com.sadna.group13a.domain.Events.RaffleWonEvent;
import com.sadna.group13a.domain.Events.StaffNominatedEvent;
import com.sadna.group13a.domain.Events.StaffRemovedEvent;
import com.sadna.group13a.domain.Events.UserBannedEvent;
import com.sadna.group13a.domain.Events.UserSuspendedEvent;
import com.sadna.group13a.domain.Events.UserReactivatedEvent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventListener {

    private final INotificationService notificationService;

    public NotificationEventListener(
            @Qualifier("webSocketNotificationService") INotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // ── Existing ──────────────────────────────────────────────────

    @EventListener
    public void onQueueTurnArrived(QueueTurnArrivedEvent event) {
        notificationService.notifyQueueTurnArrived(event.userId(), event.eventId(), event.expiresAt());
    }

    @EventListener
    public void onOrderCompleted(OrderCompletedEvent event) {
        notificationService.notifyOrderCompleted(event.userId(), event.receiptId(), event.totalPaid());
    }

    @EventListener
    public void onUserBanned(UserBannedEvent event) {
        notificationService.notifyUserBanned(event.targetUserId(), event.adminId());
    }

    @EventListener
    public void onUserSuspended(UserSuspendedEvent event) {
        notificationService.notifyUserSuspended(event.targetUserId(), event.suspendedUntil());
    }

    @EventListener
    public void onCompanyClosed(CompanyClosedByAdminEvent event) {
        notificationService.notifyCompanyClosed(event.staffUserIds(), event.companyId(), event.adminId());
    }

    @EventListener
    public void onRaffleDrawn(RaffleDrawnEvent event) {
        notificationService.notifyRaffleDrawn(event.eventId(), event.winnerCount());
    }

    @EventListener
    public void onCheckoutFailed(CheckoutFailedEvent event) {
        notificationService.notifyActionFailed(event.userId(), event.reason());
    }

    // ── New ───────────────────────────────────────────────────────

    @EventListener
    public void onCompanySuspended(CompanySuspendedEvent event) {
        notificationService.notifyCompanySuspended(event.staffUserIds(), event.companyId());
    }

    @EventListener
    public void onCompanyReopened(CompanyReopenedEvent event) {
        notificationService.notifyCompanyReopened(event.staffUserIds(), event.companyId());
    }

    @EventListener
    public void onStaffNominated(StaffNominatedEvent event) {
        notificationService.notifyStaffNominated(
                event.targetUserId(), event.companyId(), event.role().name());
    }

    @EventListener
    public void onStaffRemoved(StaffRemovedEvent event) {
        event.removedUserIds().forEach(uid ->
                notificationService.notifyStaffRemoved(uid, event.companyId()));
    }

    @EventListener
    public void onPermissionsUpdated(PermissionsUpdatedEvent event) {
        notificationService.notifyPermissionsUpdated(event.targetUserId(), event.companyId());
    }

    @EventListener
    public void onCartExpired(CartExpiredEvent event) {
        notificationService.notifyCartExpired(event.userId());
    }

    @EventListener
    public void onEventCancelled(EventCancelledEvent event) {
        notificationService.notifyEventCancelled(event.buyerIds(), event.eventId(), event.eventTitle());
    }

    @EventListener
    public void onRefundIssued(RefundIssuedEvent event) {
        notificationService.notifyRefundIssued(
                event.userId(), event.receiptId(), event.amount(), event.eventTitle());
    }

    @EventListener
    public void onEventRescheduled(EventRescheduledEvent event) {
        notificationService.notifyEventRescheduled(
                event.buyerIds(), event.eventId(), event.eventTitle(), event.newDate());
    }

    @EventListener
    public void onUserReactivated(UserReactivatedEvent event) {
        notificationService.notifyUserReactivated(event.userId());
    }

    @EventListener
    public void onEventSoldOut(EventSoldOutEvent event) {
        notificationService.notifyEventSoldOut(event.staffUserIds(), event.eventId(), event.eventTitle());
    }

    @EventListener
    public void onRaffleWon(RaffleWonEvent event) {
        notificationService.notifyRaffleWon(
                event.userId(), event.eventId(), event.authCode(), event.expiresAt());
    }

    @EventListener
    public void onAdminMessage(AdminMessageEvent event) {
        notificationService.notifyAdminMessage(event.targetUserId(), event.message());
    }
}
