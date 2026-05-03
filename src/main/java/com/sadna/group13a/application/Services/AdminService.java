package com.sadna.group13a.application.Services;

import com.sadna.group13a.application.DTO.OrderHistoryDTO;
import com.sadna.group13a.application.DTO.OrderHistoryItemDTO;
import com.sadna.group13a.application.DTO.QueueStatusDTO;
import com.sadna.group13a.application.DTO.SystemAnalyticsDTO;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import com.sadna.group13a.domain.Aggregates.Event.Event;
import com.sadna.group13a.domain.Aggregates.TicketQueue.TicketQueue;
import com.sadna.group13a.domain.Aggregates.User.Admin;
import com.sadna.group13a.domain.Aggregates.User.User;
import com.sadna.group13a.domain.Events.CompanyClosedByAdminEvent;
import com.sadna.group13a.domain.Events.UserBannedEvent;
import com.sadna.group13a.domain.Aggregates.OrderHistory.OrderHistory;
import com.sadna.group13a.domain.Interfaces.ICompanyRepository;
import com.sadna.group13a.domain.Interfaces.IEventRepository;
import com.sadna.group13a.domain.Interfaces.IOrderHistoryRepository;
import com.sadna.group13a.domain.Interfaces.IQueueRepository;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private static final Logger logger = LoggerFactory.getLogger(AdminService.class);

    private final IUserRepository userRepository;
    private final IEventRepository eventRepository;
    private final ICompanyRepository companyRepository;
    private final IQueueRepository queueRepository;
    private final IOrderHistoryRepository historyRepository;
    private final IAuth authGateway;
    private final ApplicationEventPublisher eventPublisher;

    public AdminService(IUserRepository userRepository,
                        IEventRepository eventRepository,
                        ICompanyRepository companyRepository,
                        IQueueRepository queueRepository,
                        IOrderHistoryRepository historyRepository,
                        IAuth authGateway,
                        ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
        this.companyRepository = companyRepository;
        this.queueRepository = queueRepository;
        this.historyRepository = historyRepository;
        this.authGateway = authGateway;
        this.eventPublisher = eventPublisher;
    }

    // ── User Management ───────────────────────────────────────────

    /**
     * Deactivates a member account (ban). Automatically strips all company roles
     * by making the user inactive — company-level checks gate on isActive().
     */
    public Result<Void> deactivateUser(String token, String targetUsername) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized deactivateUser attempt.");
            return Result.failure("Unauthorized: Invalid token.");
        }
        if (!isAdmin(token)) return Result.failure("Only admins can deactivate users.");

        Optional<User> targetOpt = userRepository.findByUsername(targetUsername);
        if (targetOpt.isEmpty()) return Result.failure("Target user not found.");

        User target = targetOpt.get();
        if (target instanceof Admin) return Result.failure("Cannot deactivate another admin.");

        String adminId = authGateway.extractUserId(token);
        Admin admin = (Admin) userRepository.findById(adminId).orElseThrow();

        try {
            admin.deactivateUser(target);
            userRepository.save(target);
            eventPublisher.publishEvent(new UserBannedEvent(target.getId(), adminId));
            logger.info("Admin {} deactivated user '{}'.", adminId, targetUsername);
            return Result.success();
        } catch (Exception e) {
            return Result.failure("Failed to deactivate user: " + e.getMessage());
        }
    }

    /**
     * Reactivates a previously deactivated member account.
     */
    public Result<Void> reactivateUser(String token, String targetUsername) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized reactivateUser attempt.");
            return Result.failure("Unauthorized: Invalid token.");
        }
        if (!isAdmin(token)) return Result.failure("Only admins can reactivate users.");

        Optional<User> targetOpt = userRepository.findByUsername(targetUsername);
        if (targetOpt.isEmpty()) return Result.failure("Target user not found.");

        User target = targetOpt.get();
        String adminId = authGateway.extractUserId(token);
        Admin admin = (Admin) userRepository.findById(adminId).orElseThrow();

        admin.activateUser(target);
        userRepository.save(target);
        logger.info("Admin {} reactivated user '{}'.", adminId, targetUsername);
        return Result.success();
    }

    // ── Event Management ──────────────────────────────────────────

    /**
     * Cancels (unpublishes) an event globally. Should be followed by refunds in a full implementation.
     */
    public Result<Void> cancelEventGlobally(String token, String eventId) {
        if (!authGateway.validateToken(token)) return Result.failure("Unauthorized: Invalid token.");
        if (!isAdmin(token)) return Result.failure("Only admins can cancel events.");

        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) return Result.failure("Event not found.");

        Event event = eventOpt.get();
        event.unpublish();
        eventRepository.save(event);

        logger.warn("Admin {} cancelled event {}.", authGateway.extractUserId(token), eventId);
        return Result.success();
    }

    // ── Company Management ────────────────────────────────────────

    /**
     * Force-closes a production company regardless of who the founder is.
     * Notifications to staff are the caller's responsibility (domain events / notification service).
     */
    public Result<Void> closeCompanyGlobally(String token, String companyId) {
        if (!authGateway.validateToken(token)) return Result.failure("Unauthorized: Invalid token.");
        if (!isAdmin(token)) return Result.failure("Only admins can force-close companies.");

        Optional<ProductionCompany> companyOpt = companyRepository.findById(companyId);
        if (companyOpt.isEmpty()) return Result.failure("Company not found.");

        String adminId = authGateway.extractUserId(token);
        ProductionCompany company = companyOpt.get();
        company.forceClose();
        companyRepository.save(company);
        eventPublisher.publishEvent(new CompanyClosedByAdminEvent(companyId, adminId));

        logger.warn("Admin {} force-closed company {}.", adminId, companyId);
        return Result.success();
    }

    // ── Queue Control ─────────────────────────────────────────────

    /**
     * Returns all active queues in the system so the admin can monitor load.
     */
    public Result<List<TicketQueue>> viewAllQueues(String token) {
        if (!authGateway.validateToken(token)) return Result.failure("Unauthorized.");
        if (!isAdmin(token)) return Result.failure("Only admins can view all queues.");

        return Result.success(queueRepository.findAll());
    }

    /**
     * Clears a specific event queue (e.g. after a technical failure).
     */
    public Result<Void> clearEventQueue(String token, String eventId) {
        if (!authGateway.validateToken(token)) return Result.failure("Unauthorized.");
        if (!isAdmin(token)) return Result.failure("Only admins can clear queues.");

        Optional<TicketQueue> queueOpt = queueRepository.findByEventId(eventId);
        if (queueOpt.isEmpty()) return Result.failure("No queue found for event " + eventId);

        TicketQueue queue = queueOpt.get();
        queue.clearQueue();
        queueRepository.save(queue);

        logger.warn("Admin {} cleared queue for event {}.", authGateway.extractUserId(token), eventId);
        return Result.success();
    }

    /**
     * Adjusts how many users may be in the active (purchasing) state simultaneously for an event.
     */
    public Result<Void> adjustQueueRate(String token, String eventId, int newMaxConcurrentUsers) {
        if (!authGateway.validateToken(token)) return Result.failure("Unauthorized.");
        if (!isAdmin(token)) return Result.failure("Only admins can adjust queue rate.");

        Optional<TicketQueue> queueOpt = queueRepository.findByEventId(eventId);
        if (queueOpt.isEmpty()) return Result.failure("No queue found for event " + eventId);

        try {
            TicketQueue queue = queueOpt.get();
            queue.adjustMaxConcurrentUsers(newMaxConcurrentUsers);
            queueRepository.save(queue);
            logger.info("Admin adjusted queue rate for event {} to {}.", eventId, newMaxConcurrentUsers);
            return Result.success();
        } catch (IllegalArgumentException e) {
            return Result.failure(e.getMessage());
        }
    }

    // ── Analytics ─────────────────────────────────────────────────

    /**
     * Returns a snapshot of key system metrics.
     */
    public Result<SystemAnalyticsDTO> getSystemAnalytics(String token) {
        if (!authGateway.validateToken(token)) return Result.failure("Unauthorized.");
        if (!isAdmin(token)) return Result.failure("Only admins can view system analytics.");

        int totalUsers = userRepository.findAll().size();
        int activeQueues = queueRepository.findAll().size();
        int activeCompanies = (int) companyRepository.findAll().stream()
                .filter(c -> c.getStatus().name().equals("ACTIVE"))
                .count();
        int publishedEvents = eventRepository.findPublished().size();

        SystemAnalyticsDTO dto = new SystemAnalyticsDTO(totalUsers, activeQueues, activeCompanies, publishedEvents);
        return Result.success(dto);
    }

    public Result<List<OrderHistoryDTO>> viewGlobalPurchaseHistory(String token) {
        if (!authGateway.validateToken(token)) return Result.failure("Unauthorized.");
        if (!isAdmin(token)) return Result.failure("Only admins can view global purchase history.");

        List<OrderHistoryDTO> dtos = historyRepository.findAll().stream()
                .map(h -> new OrderHistoryDTO(
                        h.getReceiptId(),
                        h.getUserId(),
                        h.getPurchaseDate(),
                        h.getTotalPaid(),
                        h.getItems().stream()
                                .map(i -> new OrderHistoryItemDTO(
                                        i.getEventId(),
                                        i.getEventTitle(),
                                        i.getEventDate(),
                                        i.getCompanyName(),
                                        i.getZoneName(),
                                        i.getSeatLabel(),
                                        i.getPricePaid()))
                                .collect(Collectors.toList())))
                .collect(Collectors.toList());
        return Result.success(dtos);
    }

    // ── Private helpers ───────────────────────────────────────────

    private boolean isAdmin(String token) {
        String userId = authGateway.extractUserId(token);
        return userRepository.findById(userId)
                .map(u -> u instanceof Admin && u.isActive())
                .orElse(false);
    }
}