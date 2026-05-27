package com.sadna.group13a.infrastructure;

import com.sadna.group13a.application.Interfaces.INotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class InMemoryNotificationService implements INotificationService {

    private static final Logger logger = LoggerFactory.getLogger(InMemoryNotificationService.class);

    @Override
    public void notifyQueueTurnArrived(String userId, String eventId, LocalDateTime expiresAt) {
        logger.info("[NOTIFY] User {} — it's your turn for event {}. Purchase window closes at {}.",
                userId, eventId, expiresAt);
    }

    @Override
    public void notifyOrderCompleted(String userId, String receiptId, double totalPaid) {
        logger.info("[NOTIFY] User {} — order confirmed. Receipt: {}, total paid: {:.2f}.",
                userId, receiptId, totalPaid);
    }

    @Override
    public void notifyUserBanned(String userId, String adminId) {
        logger.warn("[NOTIFY] User {} — your account has been deactivated by admin {}.",
                userId, adminId);
    }

    @Override
    public void notifyUserSuspended(String userId, java.time.LocalDateTime suspendedUntil) {
        String when = (suspendedUntil != null) ? "until " + suspendedUntil : "indefinitely";
        logger.warn("[NOTIFY] User {} — your account has been suspended {} (view-only).", userId, when);
    }

    @Override
    public void notifyCompanyClosed(List<String> staffIds, String companyId, String adminId) {
        staffIds.forEach(uid ->
            logger.warn("[NOTIFY] User {} — company {} has been force-closed by admin {}.",
                    uid, companyId, adminId));
    }

    @Override
    public void notifyRaffleDrawn(String eventId, int winnerCount) {
        logger.info("[NOTIFY] Raffle draw complete for event {}. {} winner(s) selected.",
                eventId, winnerCount);
    }

    @Override
    public void notifyActionFailed(String userId, String reason) {
        logger.warn("[NOTIFY] User {} — action failed: {}.", userId, reason);
    }

    @Override
    public void notifyCompanySuspended(List<String> staffIds, String companyId) {
        staffIds.forEach(uid ->
            logger.warn("[NOTIFY] User {} — company {} has been suspended.", uid, companyId));
    }

    @Override
    public void notifyCompanyReopened(List<String> staffIds, String companyId) {
        staffIds.forEach(uid ->
            logger.info("[NOTIFY] User {} — company {} has been reopened.", uid, companyId));
    }

    @Override
    public void notifyStaffNominated(String userId, String companyId, String role) {
        logger.info("[NOTIFY] User {} — you have been nominated for the role of {} in company {}.",
                userId, role, companyId);
    }

    @Override
    public void notifyStaffRemoved(String userId, String companyId) {
        logger.warn("[NOTIFY] User {} — your role in company {} has been removed.", userId, companyId);
    }

    @Override
    public void notifyPermissionsUpdated(String userId, String companyId) {
        logger.info("[NOTIFY] User {} — your permissions in company {} have been updated.", userId, companyId);
    }

    @Override
    public void notifyCartExpired(String userId) {
        logger.warn("[NOTIFY] User {} — your reserved cart has expired and seats have been released.", userId);
    }

    @Override
    public void notifyEventCancelled(List<String> buyerIds, String eventId, String eventTitle) {
        buyerIds.forEach(uid ->
            logger.warn("[NOTIFY] User {} — the event \"{}\" ({}) has been cancelled.", uid, eventTitle, eventId));
    }

    @Override
    public void notifyRefundIssued(String userId, String receiptId, double amount, String eventTitle) {
        logger.info("[NOTIFY] User {} — refunded {} for \"{}\" (receipt {}).",
                userId, amount, eventTitle, receiptId);
    }

    @Override
    public void notifyEventRescheduled(List<String> buyerIds, String eventId, String eventTitle,
                                       LocalDateTime newDate) {
        buyerIds.forEach(uid ->
            logger.info("[NOTIFY] User {} — the event \"{}\" ({}) has been rescheduled to {}.",
                    uid, eventTitle, eventId, newDate));
    }

    @Override
    public void notifyUserReactivated(String userId) {
        logger.info("[NOTIFY] User {} — your account has been reactivated.", userId);
    }

    @Override
    public void notifyEventSoldOut(List<String> staffIds, String eventId, String eventTitle) {
        staffIds.forEach(uid ->
            logger.info("[NOTIFY] User {} — the event \"{}\" ({}) is now sold out.", uid, eventTitle, eventId));
    }

    @Override
    public void notifyRaffleWon(String userId, String eventId, String authCode, LocalDateTime expiresAt) {
        logger.info("[NOTIFY] User {} — congratulations! You won the raffle for event {}. " +
                "Your purchase code is {} (valid until {}).", userId, eventId, authCode, expiresAt);
    }

    @Override
    public void notifyAdminMessage(String userId, String message) {
        logger.info("[NOTIFY] User {} — message from admin: {}", userId, message);
    }
}
