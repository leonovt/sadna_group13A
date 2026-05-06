package com.sadna.group13a.application.Services;

import com.sadna.group13a.application.DTO.OrderHistoryDTO;
import com.sadna.group13a.application.DTO.OrderHistoryItemDTO;
import com.sadna.group13a.application.DTO.SystemAnalyticsDTO;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import com.sadna.group13a.domain.Aggregates.Event.Event;
import com.sadna.group13a.domain.Aggregates.TicketQueue.TicketQueue;
import com.sadna.group13a.domain.Aggregates.User.User;
import com.sadna.group13a.domain.Events.CompanyClosedByAdminEvent;
import com.sadna.group13a.domain.Events.UserBannedEvent;
import com.sadna.group13a.domain.Interfaces.IAdminRepository;
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
    private final IAdminRepository adminRepository;
    private final IEventRepository eventRepository;
    private final ICompanyRepository companyRepository;
    private final IQueueRepository queueRepository;
    private final IOrderHistoryRepository historyRepository;
    private final IAuth authGateway;
    private final ApplicationEventPublisher eventPublisher;
    private final SystemLogService systemLogService;

    public AdminService(IUserRepository userRepository,
                        IAdminRepository adminRepository,
                        IEventRepository eventRepository,
                        ICompanyRepository companyRepository,
                        IQueueRepository queueRepository,
                        IOrderHistoryRepository historyRepository,
                        IAuth authGateway,
                        ApplicationEventPublisher eventPublisher,
                        SystemLogService systemLogService) {
        this.userRepository = userRepository;
        this.adminRepository = adminRepository;
        this.eventRepository = eventRepository;
        this.companyRepository = companyRepository;
        this.queueRepository = queueRepository;
        this.historyRepository = historyRepository;
        this.authGateway = authGateway;
        this.eventPublisher = eventPublisher;
        this.systemLogService = systemLogService;
    }

    // ── User Management ───────────────────────────────────────────

    public Result<Void> deactivateUser(String token, String targetUsername) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized deactivateUser attempt.");
            return Result.failure("Unauthorized: Invalid token.");
        }
        if (!isAdmin(token)) return Result.failure("Only admins can deactivate users.");

        Optional<User> targetOpt = userRepository.findByUsername(targetUsername);
        if (targetOpt.isEmpty()) return Result.failure("Target user not found.");

        User target = targetOpt.get();
        if (adminRepository.findByUserId(target.getId()).isPresent()) {
            return Result.failure("Cannot deactivate another admin.");
        }

        String adminId = authGateway.extractUserId(token);
        target.deactivate();
        userRepository.save(target);
        eventPublisher.publishEvent(new UserBannedEvent(target.getId(), adminId));
        systemLogService.logEvent("deactivateUser adminId=" + adminId + " target=" + targetUsername);
        logger.info("Admin {} deactivated user '{}'.", adminId, targetUsername);
        return Result.success();
    }

    public Result<Void> reactivateUser(String token, String targetUsername) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized reactivateUser attempt.");
            return Result.failure("Unauthorized: Invalid token.");
        }
        if (!isAdmin(token)) return Result.failure("Only admins can reactivate users.");

        Optional<User> targetOpt = userRepository.findByUsername(targetUsername);
        if (targetOpt.isEmpty()) return Result.failure("Target user not found.");

        String adminId = authGateway.extractUserId(token);
        User target = targetOpt.get();
        target.activate();
        userRepository.save(target);
        systemLogService.logEvent("reactivateUser adminId=" + adminId + " target=" + targetUsername);
        logger.info("Admin {} reactivated user '{}'.", adminId, targetUsername);
        return Result.success();
    }

    // ── Event Management ──────────────────────────────────────────

    public Result<Void> cancelEventGlobally(String token, String eventId) {
        if (!authGateway.validateToken(token)) return Result.failure("Unauthorized: Invalid token.");
        if (!isAdmin(token)) return Result.failure("Only admins can cancel events.");

        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) return Result.failure("Event not found.");

        String adminId = authGateway.extractUserId(token);
        Event event = eventOpt.get();
        event.unpublish();
        eventRepository.save(event);
        systemLogService.logEvent("cancelEventGlobally adminId=" + adminId + " eventId=" + eventId);
        logger.warn("Admin {} cancelled event {}.", adminId, eventId);
        return Result.success();
    }

    // ── Company Management ────────────────────────────────────────

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
        systemLogService.logEvent("closeCompanyGlobally adminId=" + adminId + " companyId=" + companyId);
        logger.warn("Admin {} force-closed company {}.", adminId, companyId);
        return Result.success();
    }

    // ── Queue Control ─────────────────────────────────────────────

    public Result<List<TicketQueue>> viewAllQueues(String token) {
        if (!authGateway.validateToken(token)) return Result.failure("Unauthorized.");
        if (!isAdmin(token)) return Result.failure("Only admins can view all queues.");
        return Result.success(queueRepository.findAll());
    }

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

    public Result<SystemAnalyticsDTO> getSystemAnalytics(String token) {
        if (!authGateway.validateToken(token)) return Result.failure("Unauthorized.");
        if (!isAdmin(token)) return Result.failure("Only admins can view system analytics.");

        int totalUsers = userRepository.findAll().size();
        int activeQueues = queueRepository.findAll().size();
        int activeCompanies = (int) companyRepository.findAll().stream()
                .filter(c -> c.getStatus().name().equals("ACTIVE"))
                .count();
        int publishedEvents = eventRepository.findPublished().size();

        return Result.success(new SystemAnalyticsDTO(totalUsers, activeQueues, activeCompanies, publishedEvents));
    }

    public Result<List<OrderHistoryDTO>> viewGlobalPurchaseHistory(String token) {
        if (!authGateway.validateToken(token)) return Result.failure("Unauthorized.");
        if (!isAdmin(token)) return Result.failure("Only admins can view global purchase history.");

        List<OrderHistoryDTO> dtos = historyRepository.findAll().stream()
                .map(h -> new OrderHistoryDTO(
                        h.getReceiptId(), h.getUserId(), h.getPurchaseDate(), h.getTotalPaid(),
                        h.getItems().stream()
                                .map(i -> new OrderHistoryItemDTO(i.getEventId(), i.getEventTitle(),
                                        i.getEventDate(), i.getCompanyName(), i.getZoneName(),
                                        i.getSeatLabel(), i.getPricePaid()))
                                .collect(Collectors.toList())))
                .collect(Collectors.toList());
        return Result.success(dtos);
    }

    // ── Logs (SLR-8) ─────────────────────────────────────────────

    public Result<List<String>> getEventLog(String token) {
        if (!authGateway.validateToken(token)) return Result.failure("Unauthorized.");
        if (!isAdmin(token)) return Result.failure("Only admins can view event logs.");
        return Result.success(systemLogService.getEventLog());
    }

    public Result<List<String>> getErrorLog(String token) {
        if (!authGateway.validateToken(token)) return Result.failure("Unauthorized.");
        if (!isAdmin(token)) return Result.failure("Only admins can view error logs.");
        return Result.success(systemLogService.getErrorLog());
    }

    // ── Private helpers ───────────────────────────────────────────

    private boolean isAdmin(String token) {
        String userId = authGateway.extractUserId(token);
        return adminRepository.findByUserId(userId).isPresent()
                && userRepository.findById(userId).map(com.sadna.group13a.domain.Aggregates.User.User::isActive).orElse(false);
    }
}
