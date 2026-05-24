package com.sadna.group13a.application.Services;

import com.sadna.group13a.application.DTO.QueueStatusDTO;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.domain.Aggregates.Company.CompanyPermission;
import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import com.sadna.group13a.domain.Aggregates.Event.Event;
import com.sadna.group13a.domain.Aggregates.Event.EventSaleMode;
import com.sadna.group13a.domain.Aggregates.TicketQueue.QueueTicket;
import com.sadna.group13a.domain.Aggregates.TicketQueue.TicketQueue;
import com.sadna.group13a.domain.Aggregates.User.User;
import com.sadna.group13a.domain.Interfaces.IAdminRepository;
import com.sadna.group13a.domain.Events.QueueTurnArrivedEvent;
import com.sadna.group13a.domain.Interfaces.ICompanyRepository;
import com.sadna.group13a.domain.Interfaces.IEventRepository;
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
    private final IEventRepository eventRepository;
    private final ICompanyRepository companyRepository;
    private final IUserRepository userRepository;
    private final IAdminRepository adminRepository;
    private final IAuth authGateway;
    private final ApplicationEventPublisher eventPublisher;

    public QueueService(IQueueRepository queueRepository,
                        IEventRepository eventRepository,
                        ICompanyRepository companyRepository,
                        IUserRepository userRepository,
                        IAdminRepository adminRepository,
                        IAuth authGateway,
                        ApplicationEventPublisher eventPublisher) {
        this.queueRepository = queueRepository;
        this.eventRepository = eventRepository;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.adminRepository = adminRepository;
        this.authGateway = authGateway;
        this.eventPublisher = eventPublisher;
    }

    // ── Owner / Admin Commands ────────────────────────────────────

    /**
     * Creates a virtual queue for an event. Called by the event owner when enabling queue mode.
     */
    public Result<Void> createQueue(String token, String eventId, int maxConcurrentUsers) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized createQueue attempt for event '{}'.", eventId);
            return Result.failure("Unauthorized.");
        }
        String initiatorId = authGateway.extractUserId(token);

        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) {
            logger.warn("User '{}' tried to create queue for non-existent event '{}'.", initiatorId, eventId);
            return Result.failure("Event not found.");
        }

        Event event = eventOpt.get();
        Optional<ProductionCompany> compOpt = companyRepository.findById(event.getCompanyId());
        if (compOpt.isEmpty()) {
            logger.warn("User '{}' tried to create queue for event '{}' but company '{}' not found.", initiatorId, eventId, event.getCompanyId());
            return Result.failure("User lacks permission to manage events.");
        }
        if (!compOpt.get().hasPermission(initiatorId, CompanyPermission.MANAGE_EVENTS)) {
            logger.warn("User '{}' lacks MANAGE_EVENTS permission — createQueue for event '{}' denied.", initiatorId, eventId);
            return Result.failure("User lacks permission to manage events.");
        }

        if (queueRepository.findByEventId(eventId).isPresent()) {
            logger.warn("User '{}' tried to create queue but one already exists for event '{}'.", initiatorId, eventId);
            return Result.failure("A queue already exists for this event.");
        }
        try {
            event.setSaleMode(EventSaleMode.QUEUE);
        } catch (Exception e) {
            logger.warn("User '{}' failed to set QUEUE sale mode for event '{}': {}", initiatorId, eventId, e.getMessage());
            return Result.failure(e.getMessage());
        }

        TicketQueue queue = new TicketQueue(eventId, maxConcurrentUsers);
        queueRepository.save(queue);
        eventRepository.save(event);
        logger.info("User '{}' created queue for event '{}' with max {} concurrent users.", initiatorId, eventId, maxConcurrentUsers);
        return Result.success();
    }

    /**
     * Admin operation: clears all waiting and active users from a queue.
     */
    public Result<Void> clearQueue(String token, String eventId) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized clearQueue attempt for event '{}'.", eventId);
            return Result.failure("Unauthorized.");
        }
        String adminId = authGateway.extractUserId(token);
        if (!isAdmin(token)) {
            logger.warn("Non-admin user '{}' attempted to clear queue for event '{}'.", adminId, eventId);
            return Result.failure("Only system admins can clear queues.");
        }

        Optional<TicketQueue> queueOpt = queueRepository.findByEventId(eventId);
        if (queueOpt.isEmpty()) {
            logger.warn("Admin '{}' tried to clear queue for event '{}' but no queue exists.", adminId, eventId);
            return Result.failure("No queue found for event " + eventId);
        }

        TicketQueue queue = queueOpt.get();
        queue.clearQueue();
        queueRepository.save(queue);

        logger.warn("Admin '{}' cleared queue for event '{}'.", adminId, eventId);
        return Result.success();
    }

    /**
     * Admin operation: adjusts how many users may be in the active (purchasing) state simultaneously.
     */
    public Result<Void> adjustQueueRate(String token, String eventId, int newMaxConcurrentUsers) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized adjustQueueRate attempt for event '{}'.", eventId);
            return Result.failure("Unauthorized.");
        }
        String adminId = authGateway.extractUserId(token);
        if (!isAdmin(token)) {
            logger.warn("Non-admin user '{}' attempted to adjust queue rate for event '{}'.", adminId, eventId);
            return Result.failure("Only system admins can adjust queue rate.");
        }

        Optional<TicketQueue> queueOpt = queueRepository.findByEventId(eventId);
        if (queueOpt.isEmpty()) {
            logger.warn("Admin '{}' tried to adjust queue rate for event '{}' but no queue exists.", adminId, eventId);
            return Result.failure("No queue found for event " + eventId);
        }

        try {
            TicketQueue queue = queueOpt.get();
            queue.adjustMaxConcurrentUsers(newMaxConcurrentUsers);
            // Immediately admit more users if the limit was raised
            queue.processBatch(newMaxConcurrentUsers, DEFAULT_ACCESS_MINUTES);
            queueRepository.save(queue);

            logger.info("Admin '{}' adjusted queue rate for event '{}' to {}.", adminId, eventId, newMaxConcurrentUsers);
            return Result.success();
        } catch (IllegalArgumentException e) {
            logger.error("Admin '{}' supplied invalid queue capacity {} for event '{}': {}", adminId, newMaxConcurrentUsers, eventId, e.getMessage());
            return Result.failure(e.getMessage());
        }
    }

    /**
     * Admin operation: process the next batch manually for a specific event.
     */
    public Result<List<QueueTicket>> processBatch(String token, String eventId, int batchSize) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized processBatch attempt for event '{}'.", eventId);
            return Result.failure("Unauthorized.");
        }
        String adminId = authGateway.extractUserId(token);
        if (!isAdmin(token)) {
            logger.warn("Non-admin user '{}' attempted to manually process batch for event '{}'.", adminId, eventId);
            return Result.failure("Only system admins can manually process batches.");
        }

        Optional<TicketQueue> queueOpt = queueRepository.findByEventId(eventId);
        if (queueOpt.isEmpty()) {
            logger.warn("Admin '{}' tried to process batch for event '{}' but no queue exists.", adminId, eventId);
            return Result.failure("No queue found for event " + eventId);
        }

        TicketQueue queue = queueOpt.get();
        List<QueueTicket> granted = queue.processBatch(batchSize, DEFAULT_ACCESS_MINUTES);
        queueRepository.save(queue);

        granted.forEach(t -> eventPublisher.publishEvent(
                new QueueTurnArrivedEvent(eventId, t.getUserId(), t.getExpiresAt())));

        logger.info("Admin '{}' processed batch of {} for event '{}' — actually granted: {}.", adminId, batchSize, eventId, granted.size());
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
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized joinQueue attempt for event '{}'.", eventId);
            return Result.failure("Unauthorized.");
        }
        String userId = authGateway.extractUserId(token);

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty() || !userOpt.get().canPurchase()) {
            logger.warn("User '{}' cannot join queue for event '{}' — not an active member.", userId, eventId);
            return Result.failure("Only active members can join a queue.");
        }

        Optional<TicketQueue> queueOpt = queueRepository.findByEventId(eventId);
        if (queueOpt.isEmpty()) {
            // No queue configured — unrestricted access
            logger.debug("User '{}' joined event '{}' with no queue configured — direct access granted.", userId, eventId);
            return Result.success(new QueueStatusDTO(eventId, userId, true, 0, 0, null));
        }

        TicketQueue queue = queueOpt.get();

        // Already has active access?
        if (queue.isUserActive(userId)) {
            Optional<QueueTicket> active = queue.getActiveTicket(userId);
            LocalDateTime exp = active.map(QueueTicket::getExpiresAt).orElse(null);
            logger.debug("User '{}' rejoined queue for event '{}' — already active.", userId, eventId);
            return Result.success(new QueueStatusDTO(eventId, userId, true, 0, queue.getWaitingCount(), exp));
        }

        // Already waiting?
        Optional<QueueTicket> alreadyWaiting = queue.getWaitingTicket(userId);
        if (alreadyWaiting.isPresent()) {
            int pos = alreadyWaiting.get().getPositionInLine();
            logger.debug("User '{}' rejoined queue for event '{}' — already waiting at position {}.", userId, eventId, pos);
            return Result.success(new QueueStatusDTO(
                    eventId, userId, false, pos, queue.getWaitingCount(), null));
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
                logger.info("User '{}' granted immediate queue access for event '{}'.", userId, eventId);
                return Result.success(new QueueStatusDTO(eventId, userId, true, 0, queue.getWaitingCount(), exp));
            } else {
                Optional<QueueTicket> waiting = queue.getWaitingTicket(userId);
                int pos = waiting.map(QueueTicket::getPositionInLine).orElse(0);
                logger.info("User '{}' joined waiting line for event '{}' at position {}.", userId, eventId, pos);
                return Result.success(new QueueStatusDTO(eventId, userId, false, pos, queue.getWaitingCount(), null));
            }
        } catch (IllegalArgumentException e) {
            logger.warn("User '{}' failed to join queue for event '{}': {}", userId, eventId, e.getMessage());
            return Result.failure(e.getMessage());
        }
    }

    /**
     * Returns the user's current status in the queue without modifying it.
     */
    public Result<QueueStatusDTO> getStatus(String token, String eventId) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized getStatus attempt for event '{}'.", eventId);
            return Result.failure("Unauthorized.");
        }
        String userId = authGateway.extractUserId(token);

        Optional<TicketQueue> queueOpt = queueRepository.findByEventId(eventId);
        if (queueOpt.isEmpty()) {
            logger.debug("getStatus: no queue for event '{}' — reporting direct access for user '{}'.", eventId, userId);
            return Result.success(new QueueStatusDTO(eventId, userId, true, 0, 0, null));
        }

        TicketQueue queue = queueOpt.get();

        if (queue.isUserActive(userId)) {
            Optional<QueueTicket> ticket = queue.getActiveTicket(userId);
            LocalDateTime exp = ticket.map(QueueTicket::getExpiresAt).orElse(null);
            logger.debug("getStatus: user '{}' is active in queue for event '{}'.", userId, eventId);
            return Result.success(new QueueStatusDTO(eventId, userId, true, 0, queue.getWaitingCount(), exp));
        }

        Optional<QueueTicket> waiting = queue.getWaitingTicket(userId);
        if (waiting.isPresent()) {
            int pos = waiting.get().getPositionInLine();
            logger.debug("getStatus: user '{}' is waiting at position {} for event '{}'.", userId, pos, eventId);
            return Result.success(new QueueStatusDTO(
                    eventId, userId, false, pos, queue.getWaitingCount(), null));
        }

        logger.debug("getStatus: user '{}' is not in queue for event '{}'.", userId, eventId);
        return Result.success(new QueueStatusDTO(eventId, userId, false, -1, queue.getWaitingCount(), null));
    }

    /**
     * Removes a user from the active set when they finish or abandon the checkout process.
     */
    public Result<Void> releaseAccess(String token, String eventId) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized releaseAccess attempt for event '{}'.", eventId);
            return Result.failure("Unauthorized.");
        }
        String userId = authGateway.extractUserId(token);

        Optional<TicketQueue> queueOpt = queueRepository.findByEventId(eventId);
        if (queueOpt.isEmpty()) {
            logger.debug("releaseAccess: no queue for event '{}' — nothing to release for user '{}'.", eventId, userId);
            return Result.success();
        }

        TicketQueue queue = queueOpt.get();
        queue.removeActiveUser(userId);
        queue.removeWaitingUser(userId);
        // Admit the next user now that a freed active slot exists
        List<QueueTicket> admitted = queue.processBatch(1, DEFAULT_ACCESS_MINUTES);
        queueRepository.save(queue);

        admitted.forEach(t -> eventPublisher.publishEvent(
                new QueueTurnArrivedEvent(eventId, t.getUserId(), t.getExpiresAt())));

        logger.info("User '{}' released queue access for event '{}'; {} user(s) admitted.", userId, eventId, admitted.size());
        return Result.success();
    }

    // ── Queries (Admin) ───────────────────────────────────────────

    /**
     * Returns all active queues in the system. Admin-only.
     */
    public Result<List<TicketQueue>> getAllQueues(String token) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized getAllQueues attempt.");
            return Result.failure("Unauthorized.");
        }
        String adminId = authGateway.extractUserId(token);
        if (!isAdmin(token)) {
            logger.warn("Non-admin user '{}' attempted to view all queues.", adminId);
            return Result.failure("Only system admins can view all queues.");
        }

        List<TicketQueue> queues = queueRepository.findAll();
        logger.debug("Admin '{}' retrieved all queues ({} total).", adminId, queues.size());
        return Result.success(queues);
    }

    // ── Private helpers ───────────────────────────────────────────

    private boolean isAdmin(String token) {
        String userId = authGateway.extractUserId(token);
        return adminRepository.findByUserId(userId).isPresent()
                && userRepository.findById(userId).map(User::isActive).orElse(false);
    }
}
