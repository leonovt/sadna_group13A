package com.sadna.group13a.infrastructure.notification;

import com.sadna.group13a.application.Interfaces.INotificationService;
import com.sadna.group13a.domain.Interfaces.ICompanyRepository;
import com.sadna.group13a.domain.Interfaces.IEventRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Primary
@Service
public class WebSocketNotificationService implements INotificationService {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("d MMM yyyy 'at' HH:mm");

    private final NotificationBroadcaster broadcaster;
    private final IEventRepository eventRepository;
    private final ICompanyRepository companyRepository;

    public WebSocketNotificationService(NotificationBroadcaster broadcaster,
                                        IEventRepository eventRepository,
                                        ICompanyRepository companyRepository) {
        this.broadcaster = broadcaster;
        this.eventRepository = eventRepository;
        this.companyRepository = companyRepository;
    }

    private String eventName(String eventId) {
        return eventRepository.findById(eventId)
                .map(e -> e.getTitle())
                .orElse(eventId);
    }

    private String companyName(String companyId) {
        return companyRepository.findById(companyId)
                .map(c -> c.getName())
                .orElse(companyId);
    }

    private String fmt(LocalDateTime dt) {
        return dt != null ? dt.format(DATE_FMT) : "unknown time";
    }

    private String money(double amount) {
        return String.format("%.2f", amount);
    }

    @Override
    public void notifyQueueTurnArrived(String userId, String eventId, LocalDateTime expiresAt) {
        broadcaster.send(userId, "It's your turn for \"" + eventName(eventId) +
                "\"! Purchase window closes at " + fmt(expiresAt) + ".");
    }

    @Override
    public void notifyOrderCompleted(String userId, String receiptId, double totalPaid) {
        broadcaster.send(userId, "Order confirmed! Total paid: " + money(totalPaid) +
                ". Receipt ID: " + receiptId + ".");
    }

    @Override
    public void notifyUserBanned(String userId, String adminId) {
        broadcaster.send(userId, "Your account has been permanently suspended by an administrator.");
    }

    @Override
    public void notifyUserSuspended(String userId, LocalDateTime suspendedUntil) {
        String when = (suspendedUntil != null) ? "until " + fmt(suspendedUntil) : "indefinitely";
        broadcaster.send(userId, "Your account has been suspended " + when +
                ". You can browse but cannot make purchases during this period.");
    }

    @Override
    public void notifyCompanyClosed(List<String> staffIds, String companyId, String adminId) {
        String name = companyName(companyId);
        staffIds.forEach(uid ->
            broadcaster.send(uid, "\"" + name + "\" has been force-closed by a system administrator."));
    }

    @Override
    public void notifyRaffleDrawn(String eventId, int winnerCount) {
        broadcaster.send(eventId, "Raffle results for \"" + eventName(eventId) +
                "\" are in — " + winnerCount + " winner(s) selected.");
    }

    @Override
    public void notifyActionFailed(String userId, String reason) {
        broadcaster.send(userId, "Action failed: " + reason);
    }

    @Override
    public void notifyCompanySuspended(List<String> staffIds, String companyId) {
        String name = companyName(companyId);
        staffIds.forEach(uid ->
            broadcaster.send(uid, "\"" + name + "\" has been suspended by an administrator."));
    }

    @Override
    public void notifyCompanyReopened(List<String> staffIds, String companyId) {
        String name = companyName(companyId);
        staffIds.forEach(uid ->
            broadcaster.send(uid, "\"" + name + "\" has been reopened and is active again."));
    }

    @Override
    public void notifyStaffNominated(String userId, String companyId, String role) {
        broadcaster.send(userId, "You have been nominated as " + role +
                " at \"" + companyName(companyId) + "\". Please accept or reject the nomination.");
    }

    @Override
    public void notifyStaffRemoved(String userId, String companyId) {
        broadcaster.send(userId, "Your role at \"" + companyName(companyId) + "\" has been removed.");
    }

    @Override
    public void notifyPermissionsUpdated(String userId, String companyId) {
        broadcaster.send(userId, "Your permissions at \"" + companyName(companyId) + "\" have been updated.");
    }

    @Override
    public void notifyCartExpired(String userId) {
        broadcaster.send(userId, "Your cart has expired and your held seats have been released.");
    }

    @Override
    public void notifyEventCancelled(List<String> buyerIds, String eventId, String eventTitle) {
        buyerIds.forEach(uid ->
            broadcaster.send(uid, "\"" + eventTitle + "\" has been cancelled. Any refunds will be processed shortly."));
    }

    @Override
    public void notifyRefundIssued(String userId, String receiptId, double amount, String eventTitle) {
        broadcaster.send(userId, "You have been refunded " + money(amount) +
                " for \"" + eventTitle + "\".");
    }

    @Override
    public void notifyEventRescheduled(List<String> buyerIds, String eventId, String eventTitle,
                                       LocalDateTime newDate) {
        buyerIds.forEach(uid ->
            broadcaster.send(uid, "\"" + eventTitle + "\" has been rescheduled to " + fmt(newDate) + "."));
    }

    @Override
    public void notifyUserReactivated(String userId) {
        broadcaster.send(userId, "Your account has been reactivated. Welcome back!");
    }

    @Override
    public void notifyEventSoldOut(List<String> staffIds, String eventId, String eventTitle) {
        staffIds.forEach(uid ->
            broadcaster.send(uid, "\"" + eventTitle + "\" is now sold out."));
    }

    @Override
    public void notifyRaffleWon(String userId, String eventId, String authCode, LocalDateTime expiresAt) {
        broadcaster.send(userId, "Congratulations! You won the raffle for \"" + eventName(eventId) +
                "\"! Your purchase code is: " + authCode + " (valid until " + fmt(expiresAt) + ").");
    }

    @Override
    public void notifyAdminMessage(String userId, String message) {
        broadcaster.send(userId, "Message from admin: " + message);
    }
}
