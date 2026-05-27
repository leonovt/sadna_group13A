package com.sadna.group13a.presentation.notification;

import com.sadna.group13a.application.Interfaces.INotificationService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Primary
@Service
public class WebSocketNotificationService implements INotificationService {

    private final NotificationBroadcaster broadcaster;

    public WebSocketNotificationService(NotificationBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @Override
    public void notifyQueueTurnArrived(String userId, String eventId, LocalDateTime expiresAt) {
        broadcaster.send(userId, "Your turn has arrived for event " + eventId +
                ". Purchase window closes at " + expiresAt + ".");
    }

    @Override
    public void notifyOrderCompleted(String userId, String receiptId, double totalPaid) {
        broadcaster.send(userId, "Order confirmed. Receipt: " + receiptId +
                ", total paid: " + totalPaid + ".");
    }

    @Override
    public void notifyUserBanned(String userId, String adminId) {
        broadcaster.send(userId, "Your account has been suspended.");
    }

    @Override
    public void notifyUserSuspended(String userId, java.time.LocalDateTime suspendedUntil) {
        String when = (suspendedUntil != null) ? "until " + suspendedUntil : "indefinitely";
        broadcaster.send(userId, "Your account has been suspended " + when +
                ". You can browse but cannot make purchases during this time.");
    }

    @Override
    public void notifyCompanyClosed(List<String> staffIds, String companyId, String adminId) {
        staffIds.forEach(uid ->
            broadcaster.send(uid, "Your production company has been force-closed by a system administrator."));
    }

    @Override
    public void notifyRaffleDrawn(String eventId, int winnerCount) {
        broadcaster.send(eventId, "Raffle results are available for event " + eventId +
                ". " + winnerCount + " winner(s) selected.");
    }

    @Override
    public void notifyActionFailed(String userId, String reason) {
        broadcaster.send(userId, "Action failed: " + reason);
    }

    @Override
    public void notifyCompanySuspended(List<String> staffIds, String companyId) {
        staffIds.forEach(uid ->
            broadcaster.send(uid, "Your production company has been suspended."));
    }

    @Override
    public void notifyCompanyReopened(List<String> staffIds, String companyId) {
        staffIds.forEach(uid ->
            broadcaster.send(uid, "Your production company has been reopened and is active again."));
    }

    @Override
    public void notifyStaffNominated(String userId, String companyId, String role) {
        broadcaster.send(userId, "You have been nominated for the role of " + role +
                " in company " + companyId + ". Please accept or reject the nomination.");
    }

    @Override
    public void notifyStaffRemoved(String userId, String companyId) {
        broadcaster.send(userId, "Your role in company " + companyId + " has been removed.");
    }

    @Override
    public void notifyPermissionsUpdated(String userId, String companyId) {
        broadcaster.send(userId, "Your permissions in company " + companyId + " have been updated.");
    }

    @Override
    public void notifyCartExpired(String userId) {
        broadcaster.send(userId, "Your reserved cart has expired and your held seats have been released.");
    }

    @Override
    public void notifyEventCancelled(List<String> buyerIds, String eventId, String eventTitle) {
        buyerIds.forEach(uid ->
            broadcaster.send(uid, "The event \"" + eventTitle + "\" has been cancelled."));
    }

    @Override
    public void notifyRefundIssued(String userId, String receiptId, double amount, String eventTitle) {
        broadcaster.send(userId, "You have been refunded " + amount +
                " for \"" + eventTitle + "\" (receipt " + receiptId + ").");
    }

    @Override
    public void notifyEventRescheduled(List<String> buyerIds, String eventId, String eventTitle,
                                       LocalDateTime newDate) {
        buyerIds.forEach(uid ->
            broadcaster.send(uid, "The event \"" + eventTitle + "\" has been rescheduled to " + newDate + "."));
    }

    @Override
    public void notifyUserReactivated(String userId) {
        broadcaster.send(userId, "Your account has been reactivated. Welcome back!");
    }

    @Override
    public void notifyEventSoldOut(List<String> staffIds, String eventId, String eventTitle) {
        staffIds.forEach(uid ->
            broadcaster.send(uid, "The event \"" + eventTitle + "\" is now sold out."));
    }

    @Override
    public void notifyRaffleWon(String userId, String eventId, String authCode, LocalDateTime expiresAt) {
        broadcaster.send(userId, "Congratulations! You won the raffle for event " + eventId +
                ". Your purchase code is: " + authCode + " (valid until " + expiresAt + ").");
    }

    @Override
    public void notifyAdminMessage(String userId, String message) {
        broadcaster.send(userId, "Message from admin: " + message);
    }
}
