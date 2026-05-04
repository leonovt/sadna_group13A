package com.sadna.group13a.application.EventListeners;

import com.sadna.group13a.application.Interfaces.INotificationService;
import com.sadna.group13a.domain.Events.CompanyClosedByAdminEvent;
import com.sadna.group13a.domain.Events.OrderCompletedEvent;
import com.sadna.group13a.domain.Events.QueueTurnArrivedEvent;
import com.sadna.group13a.domain.Events.RaffleDrawnEvent;
import com.sadna.group13a.domain.Events.UserBannedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventListener {

    private final INotificationService notificationService;

    public NotificationEventListener(INotificationService notificationService) {
        this.notificationService = notificationService;
    }

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
    public void onCompanyClosed(CompanyClosedByAdminEvent event) {
        notificationService.notifyCompanyClosed(event.companyId(), event.adminId());
    }

    @EventListener
    public void onRaffleDrawn(RaffleDrawnEvent event) {
        notificationService.notifyRaffleDrawn(event.eventId(), event.winnerCount());
    }
}
