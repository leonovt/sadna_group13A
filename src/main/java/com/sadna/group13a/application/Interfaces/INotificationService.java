package com.sadna.group13a.application.Interfaces;

import java.time.LocalDateTime;
import java.util.List;

public interface INotificationService {

    // ── Existing triggers ─────────────────────────────────────────

    void notifyQueueTurnArrived(String userId, String eventId, LocalDateTime expiresAt);

    void notifyOrderCompleted(String userId, String receiptId, double totalPaid);

    void notifyUserBanned(String userId, String adminId);

    /** Notifies every staff member of the company that it has been force-closed. */
    void notifyCompanyClosed(List<String> staffIds, String companyId, String adminId);

    void notifyRaffleDrawn(String eventId, int winnerCount);

    void notifyActionFailed(String userId, String reason);

    // ── New triggers ──────────────────────────────────────────────

    void notifyCompanySuspended(List<String> staffIds, String companyId);

    void notifyCompanyReopened(List<String> staffIds, String companyId);

    void notifyStaffNominated(String userId, String companyId, String role);

    void notifyStaffRemoved(String userId, String companyId);

    void notifyPermissionsUpdated(String userId, String companyId);

    void notifyCartExpired(String userId);

    void notifyEventCancelled(List<String> buyerIds, String eventId, String eventTitle);

    /** Notifies a buyer that a refund was issued (e.g. after an event cancellation). */
    void notifyRefundIssued(String userId, String receiptId, double amount, String eventTitle);

    void notifyEventRescheduled(List<String> buyerIds, String eventId, String eventTitle, LocalDateTime newDate);

    void notifyUserReactivated(String userId);

    void notifyEventSoldOut(List<String> staffIds, String eventId, String eventTitle);

    void notifyRaffleWon(String userId, String eventId, String authCode, LocalDateTime expiresAt);

    void notifyAdminMessage(String userId, String message);
}
