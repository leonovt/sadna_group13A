package com.sadna.group13a.application.Services;

import com.sadna.group13a.domain.Interfaces.IPendingNotificationRepository;
import com.sadna.group13a.infrastructure.PendingNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Application service for the deferred-notification inbox (I.6). The read-and-clear of a
 * member's pending notifications on (re)connect is a single use case and therefore runs in
 * one transaction here — never directly from a Vaadin presenter, which has no active
 * transaction and would fail the JPA {@code deleteByUserId} with a
 * {@code TransactionRequiredException}.
 */
@Service
public class PendingNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(PendingNotificationService.class);

    private final IPendingNotificationRepository repository;

    public PendingNotificationService(IPendingNotificationRepository repository) {
        this.repository = repository;
    }

    /**
     * Atomically reads and clears the pending notifications stored for a user while they were
     * offline, returning their messages (oldest-first) for delivery to the freshly attached UI.
     * Runs in its own transaction so the read and the delete are consistent and the delete has
     * an active {@code EntityManager}.
     *
     * @return the pending messages (possibly empty); never {@code null}
     */
    @Transactional
    public List<String> drainPending(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        List<PendingNotification> pending = repository.findByUserId(userId);
        if (pending.isEmpty()) {
            return List.of();
        }
        repository.deleteByUserId(userId);
        logger.debug("Drained {} pending notification(s) for user {}.", pending.size(), userId);
        return pending.stream().map(PendingNotification::message).toList();
    }
}
