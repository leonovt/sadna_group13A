package com.sadna.group13a.infrastructure;

import com.sadna.group13a.application.Interfaces.INotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

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
    public void notifyCompanyClosed(String companyId, String adminId) {
        logger.warn("[NOTIFY] Company {} has been force-closed by admin {}.",
                companyId, adminId);
    }

    @Override
    public void notifyRaffleDrawn(String eventId, int winnerCount) {
        logger.info("[NOTIFY] Raffle draw complete for event {}. {} winner(s) selected.",
                eventId, winnerCount);
    }
}
