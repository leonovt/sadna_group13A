package com.sadna.group13a.application.Interfaces;

import java.time.LocalDateTime;

public interface INotificationService {

    void notifyQueueTurnArrived(String userId, String eventId, LocalDateTime expiresAt);

    void notifyOrderCompleted(String userId, String receiptId, double totalPaid);

    void notifyUserBanned(String userId, String adminId);

    void notifyCompanyClosed(String companyId, String adminId);

    void notifyRaffleDrawn(String eventId, int winnerCount);
}
