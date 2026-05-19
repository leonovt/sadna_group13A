package com.sadna.group13a.presentation.notification;

import com.sadna.group13a.application.Interfaces.INotificationService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Primary
@Service
public class WebSocketNotificationService implements INotificationService {

    private final NotificationBroadcaster broadcaster;

    public WebSocketNotificationService(NotificationBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @Override
    public void notifyQueueTurnArrived(String userId, String eventId, LocalDateTime expiresAt) {
        broadcaster.send(userId, "Your turn has arrived for event " + eventId);
    }

    @Override
    public void notifyOrderCompleted(String userId, String receiptId, double totalPaid) {
        broadcaster.send(userId, "Order confirmed. Receipt: " + receiptId);
    }

    @Override
    public void notifyUserBanned(String userId, String adminId) {
        broadcaster.send(userId, "Your account has been suspended.");
    }

    @Override
    public void notifyCompanyClosed(String companyId, String adminId) {
        broadcaster.send(companyId, "Your company has been closed by the platform.");
    }

    @Override
    public void notifyRaffleDrawn(String eventId, int winnerCount) {
        broadcaster.send(eventId, "Raffle results are available for event " + eventId);
    }
}
