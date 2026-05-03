package com.sadna.group13a.application.Services;

import com.sadna.group13a.application.DTO.QueueStatusDTO;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.domain.Aggregates.TicketQueue.QueueTicket;
import com.sadna.group13a.domain.Aggregates.TicketQueue.TicketQueue;
import com.sadna.group13a.domain.Aggregates.User.Admin;
import com.sadna.group13a.domain.Aggregates.User.User;
import com.sadna.group13a.domain.Events.QueueTurnArrivedEvent;
import com.sadna.group13a.domain.Interfaces.IQueueRepository;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class QueueService {

    private static final Logger logger = LoggerFactory.getLogger(QueueService.class);
    private static final int DEFAULT_ACCESS_MINUTES = 10;

    private final IQueueRepository queueRepository;
    private final IUserRepository userRepository;
    private final IAuth authGateway;
    private final ApplicationEventPublisher eventPublisher;

    public QueueService(IQueueRepository queueRepository,
                        IUserRepository userRepository,
                        IAuth authGateway,
                        ApplicationEventPublisher eventPublisher) {
        this.queueRepository = queueRepository;
        this.userRepository = userRepository;
        this.authGateway = authGateway;
        this.eventPublisher = eventPublisher;
    }

    // ── Owner / Admin Commands ────────────────────────────────────

    /**
     * Creates a virtual queue for an event. Called by the event owner when enabling queue mode.
     */
    public Result<Void> createQueue(String token, String eventId, int maxConcurrentUsers) {
        if (!authGateway.validateToken(token)) return Result.failure("Unauthorized.");

        if (queueRepository.findByEventId(eventId).isPresent()) {
            return Result.failure("A queue already exists for this event.");
        }

        TicketQueue queue = new TicketQueue(eventId, maxConcurrentUsers);
        queueRepository.save(queue);
        logger.info("Queue created for event {} with max {} concurrent users.", eventId, maxConcurrentUsers);
        return Result.success();
    }

    /**
     * Admin operation: clears all waiting and active users from a queue.
     */
    public Result<Void> clearQueue(String token, String eventId) {
        if (!authGateway.validateToken(token)) return Result.failure("Unauthorized.");
        if (!isAdmin(token)) return Result.failure("Only system admins can clear queues.");

        Optional<TicketQueue> queueOpt = queueRepository.findByEventId(eventId);
        if (queueOpt.isEmpty()) return Result.failure("No queue found for event " + eventId);

        TicketQueue queue = queueOpt.get();
        queue.clearQueue();
        queueRepository.save(queue);

        logger.warn("Admin cleared queue for event {}.", eventId);
        return Result.success();
    }

    /**
     * Admin operation: adjusts how many users may be in the active (purchasing) state simultaneously.
     */
    public Result<Void> adjustQueueRate(String token, String eventId, int newMaxConcurrentUsers) {
        if (!authGateway.validateToken(token)) return Result.failure("Unauthorized.");
        if (!isAdmin(token)) return Result.failure("Only system admins can adjust queue rate.");

        Optional<TicketQueue> queueOpt = queueRepository.findByEventId(eventId);
        if (queueOpt.isEmpty()) return Result.failure("No queue found for event " + eventId);

        try {
            TicketQueue queue = queueOpt.get();
            queue.adjustMaxConcurrentUsers(newMaxConcurrentUsers);
            // Immediately admit more users if the limit was raised
            queue.processBatch(newMaxConcurrentUsers, DEFAULT_ACCESS_MINUTES);
            queueRepository.save(queue);

            logger.info("Admin adjusted queue rate for event {} to {}.", eventId, newMaxConcurrentUsers);
            return Result.success();
        } catch (IllegalArgumentException e) {
            return Result.failure(e.getMessage());
        }
    }

    /**
     * Admin operation: process the next batch manually for a specific event.
     */
    public Result<List<QueueTicket>> processBatch(String token, String eventId, int batchSize) {
        if (!authGateway.validateToken(token)) return Result.failure("Unauthorized.");
        if (!isAdmin(token)) return Result.failure("Only system admins can manually process batches.");

        Optional<TicketQueue> queueOpt = queueRepository.findByEventId(eventId);
        if (queueOpt.isEmpty()) return Result.failure("No queue found for event " + eventId);

        TicketQueue queue = queueOpt.get();
        List<QueueTicket> granted = queue.processBatch(batchSize, DEFAULT_ACCESS_MINUTES);
        queueRepository.save(queue);

        granted.forEach(t -> eventPublisher.publishEvent(
                new QueueTurnArrivedEvent(eventId, t.getUserId(), t.getExpiresAt())));

        logger.info("Admin processed batch of {} for event {}. Actually granted: {}.", batchSize, eventId, granted.size());
        return Result.success(granted);
    }

    // ── User Commands ─────────────────────────────────────────────

    /**
     * User joins the queue for an event.
     * If no queue exists for the event, direct access is granted immediately.
     * If capacity is available, the user is admitted right away.
     * Otherwise, they receive a position number and must wait.
     */
    public Result<QueueStatusDTO> joinQueue(String token, String eventId) {
        if (!authGateway.validateToken(token)) return Result.failure("Unauthorized.");
        String userId = authGateway.extractUserId(token);

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty() || !userOpt.get().isActive()) {
            return Result.failure("User not found or account is inactive.");
        }

        Optional<TicketQueue> queueOpt = queueRepository.findByEventId(eventId);
        if (queueOpt.isEmpty()) {
            // No queue configured — unrestricted access
            return Result.success(new QueueStatusDTO(eventId, userId, true, 0, 0, null));
        }

        TicketQueue queue = queueOpt.get();

        // Already has active access?
        if (queue.isUserActive(userId)) {
            Optional<QueueTicket> active = queue.getActiveTicket(userId);
            LocalDateTime exp = active.map(QueueTicket::getExpiresAt).orElse(null);
            return Result.success(new QueueStatusDTO(eventId, userId, true, 0, queue.getWaitingCount(), exp));
        }

        // Already waiting?
        Optional<QueueTicket> alreadyWaiting = queue.getWaitingTicket(userId);
        if (alreadyWaiting.isPresent()) {
            return Result.success(new QueueStatusDTO(
                    eventId, userId, false,
                    alreadyWaiting.get().getPositionInLine(),
                    queue.getWaitingCount(), null));
        }

        try {
            queue.joinQueue(userId);
            // Auto-admit if capacity allows
            List<QueueTicket> admitted = queue.processBatch(queue.getMaxConcurrentUsers(), DEFAULT_ACCESS_MINUTES);
            queueRepository.save(queue);

            admitted.forEach(t -> eventPublisher.publishEvent(
                    new QueueTurnArrivedEvent(eventId, t.getUserId(), t.getExpiresAt())));

            if (queue.isUserActive(userId)) {
                Optional<QueueTicket> ticket = queue.getActiveTicket(userId);
                LocalDateTime exp = ticket.map(QueueTicket::getExpiresAt).orElse(null);
                logger.info("User {} granted immediate access for event {}.", userId, eventId);
                return Result.success(new QueueStatusDTO(eventId, userId, true, 0, queue.getWaitingCount(), exp));
            } else {
                Optional<QueueTicket> waiting = queue.getWaitingTicket(userId);
                int pos = waiting.map(QueueTicket::getPositionInLine).orElse(0);
                logger.info("User {} is waiting at position {} for event {}.", userId, pos, eventId);
                return Result.success(new QueueStatusDTO(eventId, userId, false, pos, queue.getWaitingCount(), null));
            }
        } catch (IllegalArgumentException e) {
            return Result.failure(e.getMessage());
        }
    }

    /**
     * Returns the user's current status in the queue without modifying it.
     */
    public Result<QueueStatusDTO> getStatus(String token, String eventId) {
        if (!authGateway.validateToken(token)) return Result.failure("Unauthorized.");
        String userId = authGateway.extractUserId(token);

        Optional<TicketQueue> queueOpt = queueRepository.findByEventId(eventId);
        if (queueOpt.isEmpty()) {
            return Result.success(new QueueStatusDTO(eventId, userId, true, 0, 0, null));
        }

        TicketQueue queue = queueOpt.get();

        if (queue.isUserActive(userId)) {
            Optional<QueueTicket> ticket = queue.getActiveTicket(userId);
            LocalDateTime exp = ticket.map(QueueTicket::getExpiresAt).orElse(null);
            return Result.success(new QueueStatusDTO(eventId, userId, true, 0, queue.getWaitingCount(), exp));
        }

        Optional<QueueTicket> waiting = queue.getWaitingTicket(userId);
        if (waiting.isPresent()) {
            return Result.success(new QueueStatusDTO(
                    eventId, userId, false,
                    waiting.get().getPositionInLine(),
                    queue.getWaitingCount(), null));
        }

        return Result.success(new QueueStatusDTO(eventId, userId, false, -1, queue.getWaitingCount(), null));
    }

    /**
     * Removes a user from the active set when they finish or abandon the checkout process.
     */
    public Result<Void> releaseAccess(String token, String eventId) {
        if (!authGateway.validateToken(token)) return Result.failure("Unauthorized.");
        String userId = authGateway.extractUserId(token);

        Optional<TicketQueue> queueOpt = queueRepository.findByEventId(eventId);
        if (queueOpt.isEmpty()) return Result.success();

        TicketQueue queue = queueOpt.get();
        queue.removeActiveUser(userId);
        // Admit the next user now that a slot freed up
        List<QueueTicket> admitted = queue.processBatch(1, DEFAULT_ACCESS_MINUTES);
        queueRepository.save(queue);

        admitted.forEach(t -> eventPublisher.publishEvent(
                new QueueTurnArrivedEvent(eventId, t.getUserId(), t.getExpiresAt())));

        logger.info("User {} released access for event {}; next user admitted.", userId, eventId);
        return Result.success();
    }

    // ── Queries (Admin) ───────────────────────────────────────────

    /**
     * Returns all active queues in the system. Admin-only.
     */
    public Result<List<TicketQueue>> getAllQueues(String token) {
        if (!authGateway.validateToken(token)) return Result.failure("Unauthorized.");
        if (!isAdmin(token)) return Result.failure("Only system admins can view all queues.");

        return Result.success(queueRepository.findAll());
    }

    // ── Private helpers ───────────────────────────────────────────

    private boolean isAdmin(String token) {
        String userId = authGateway.extractUserId(token);
        return userRepository.findById(userId)
                .map(u -> u instanceof Admin && u.isActive())
                .orElse(false);
    }
}