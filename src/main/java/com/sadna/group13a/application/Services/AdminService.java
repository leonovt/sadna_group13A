package com.sadna.group13a.application.Services;

import com.sadna.group13a.application.DTO.OrderHistoryDTO;
import com.sadna.group13a.application.DTO.OrderHistoryItemDTO;
import com.sadna.group13a.application.DTO.SuspensionDTO;
import com.sadna.group13a.application.DTO.SystemAnalyticsDTO;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Interfaces.IPaymentGateway;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import com.sadna.group13a.domain.Aggregates.Event.Event;
import com.sadna.group13a.domain.Aggregates.TicketQueue.TicketQueue;
import com.sadna.group13a.domain.Aggregates.User.User;
import com.sadna.group13a.domain.Aggregates.Company.CompanyStaffMember;
import com.sadna.group13a.domain.Aggregates.OrderHistory.OrderHistory;
import com.sadna.group13a.domain.Events.AdminMessageEvent;
import com.sadna.group13a.domain.Events.CompanyClosedByAdminEvent;
import com.sadna.group13a.domain.Events.EventCancelledEvent;
import com.sadna.group13a.domain.Events.RefundIssuedEvent;
import com.sadna.group13a.domain.Events.UserBannedEvent;
import com.sadna.group13a.domain.Events.UserReactivatedEvent;
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

import java.time.Duration;
import java.time.LocalDateTime;
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
    private final IPaymentGateway paymentGateway;
    private final IAuth authGateway;
    private final ApplicationEventPublisher eventPublisher;
    private final SystemLogService systemLogService;

    public AdminService(IUserRepository userRepository,
                        IAdminRepository adminRepository,
                        IEventRepository eventRepository,
                        ICompanyRepository companyRepository,
                        IQueueRepository queueRepository,
                        IOrderHistoryRepository historyRepository,
                        IPaymentGateway paymentGateway,
                        IAuth authGateway,
                        ApplicationEventPublisher eventPublisher,
                        SystemLogService systemLogService) {
        this.userRepository = userRepository;
        this.adminRepository = adminRepository;
        this.eventRepository = eventRepository;
        this.companyRepository = companyRepository;
        this.queueRepository = queueRepository;
        this.historyRepository = historyRepository;
        this.paymentGateway = paymentGateway;
        this.authGateway = authGateway;
        this.eventPublisher = eventPublisher;
        this.systemLogService = systemLogService;
    }

    // ── User Management ───────────────────────────────────────────

    public Result<Void> deactivateUser(String token, String targetUsername) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized deactivateUser attempt for target '{}'.", targetUsername);
            return Result.failure("Unauthorized: Invalid token.");
        }
        String adminId = authGateway.extractUserId(token);
        if (!isAdmin(token)) {
            logger.warn("Non-admin user '{}' attempted to deactivate user '{}'.", adminId, targetUsername);
            return Result.failure("Only admins can deactivate users.");
        }

        Optional<User> targetOpt = userRepository.findByUsername(targetUsername);
        if (targetOpt.isEmpty()) {
            logger.warn("Admin '{}' tried to deactivate non-existent user '{}'.", adminId, targetUsername);
            return Result.failure("Target user not found.");
        }

        User target = targetOpt.get();
        if (adminRepository.findByUserId(target.getId()).isPresent()) {
            logger.warn("Admin '{}' attempted to deactivate another admin '{}'.", adminId, targetUsername);
            return Result.failure("Cannot deactivate another admin.");
        }

        target.deactivate();
        userRepository.save(target);
        eventPublisher.publishEvent(new UserBannedEvent(target.getId(), adminId));
        systemLogService.logEvent("deactivateUser adminId=" + adminId + " target=" + targetUsername);
        logger.warn("Admin '{}' deactivated user '{}'.", adminId, targetUsername);
        return Result.success();
    }

    public Result<Void> reactivateUser(String token, String targetUsername) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized reactivateUser attempt for target '{}'.", targetUsername);
            return Result.failure("Unauthorized: Invalid token.");
        }
        String adminId = authGateway.extractUserId(token);
        if (!isAdmin(token)) {
            logger.warn("Non-admin user '{}' attempted to reactivate user '{}'.", adminId, targetUsername);
            return Result.failure("Only admins can reactivate users.");
        }

        Optional<User> targetOpt = userRepository.findByUsername(targetUsername);
        if (targetOpt.isEmpty()) {
            logger.warn("Admin '{}' tried to reactivate non-existent user '{}'.", adminId, targetUsername);
            return Result.failure("Target user not found.");
        }

        User target = targetOpt.get();
        target.activate();
        userRepository.save(target);
        eventPublisher.publishEvent(new UserReactivatedEvent(target.getId(), adminId));
        systemLogService.logEvent("reactivateUser adminId=" + adminId + " target=" + targetUsername);
        logger.info("Admin '{}' reactivated user '{}'.", adminId, targetUsername);
        return Result.success();
    }

    // ── Suspension (11.6.7 / 11.6.8 / 11.6.9) ────────────────────

    /**
     * 11.6.7 — Suspend a user for a fixed number of days, or permanently when durationDays is null.
     * Suspended users keep read-only access (canPurchase returns false, browsing is unaffected).
     */
    public Result<Void> suspendUser(String token, String targetUsername, Long durationDays) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized suspendUser attempt for target '{}'.", targetUsername);
            return Result.failure("Unauthorized: Invalid token.");
        }
        String adminId = authGateway.extractUserId(token);
        if (!isAdmin(token)) {
            logger.warn("Non-admin user '{}' attempted to suspend user '{}'.", adminId, targetUsername);
            return Result.failure("Only admins can suspend users.");
        }

        Optional<User> targetOpt = userRepository.findByUsername(targetUsername);
        if (targetOpt.isEmpty()) {
            logger.warn("Admin '{}' tried to suspend non-existent user '{}'.", adminId, targetUsername);
            return Result.failure("Target user not found.");
        }

        User target = targetOpt.get();
        if (adminRepository.findByUserId(target.getId()).isPresent()) {
            logger.warn("Admin '{}' attempted to suspend another admin '{}'.", adminId, targetUsername);
            return Result.failure("Cannot suspend another admin.");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime until = (durationDays != null) ? now.plusDays(durationDays) : null;
        target.suspend(now, until);
        userRepository.save(target);
        eventPublisher.publishEvent(new UserBannedEvent(target.getId(), adminId));

        String durationDesc = (durationDays != null) ? durationDays + " day(s)" : "permanently";
        systemLogService.logEvent("suspendUser adminId=" + adminId + " target=" + targetUsername + " duration=" + durationDesc);
        logger.warn("Admin '{}' suspended user '{}' ({}).", adminId, targetUsername, durationDesc);
        return Result.success();
    }

    /**
     * 11.6.8 — Lift an active suspension (works for both temporary and permanent).
     */
    public Result<Void> liftSuspension(String token, String targetUsername) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized liftSuspension attempt for target '{}'.", targetUsername);
            return Result.failure("Unauthorized: Invalid token.");
        }
        String adminId = authGateway.extractUserId(token);
        if (!isAdmin(token)) {
            logger.warn("Non-admin user '{}' attempted to lift suspension for user '{}'.", adminId, targetUsername);
            return Result.failure("Only admins can lift suspensions.");
        }

        Optional<User> targetOpt = userRepository.findByUsername(targetUsername);
        if (targetOpt.isEmpty()) {
            logger.warn("Admin '{}' tried to lift suspension for non-existent user '{}'.", adminId, targetUsername);
            return Result.failure("Target user not found.");
        }

        User target = targetOpt.get();
        if (!target.isSuspended()) {
            return Result.failure("User '" + targetUsername + "' is not currently suspended.");
        }

        target.activate();
        userRepository.save(target);
        eventPublisher.publishEvent(new UserReactivatedEvent(target.getId(), adminId));
        systemLogService.logEvent("liftSuspension adminId=" + adminId + " target=" + targetUsername);
        logger.info("Admin '{}' lifted suspension for user '{}'.", adminId, targetUsername);
        return Result.success();
    }

    /**
     * 11.6.9 — Return suspension records for all currently suspended users,
     * including start date, duration, and end date.
     */
    public Result<List<SuspensionDTO>> viewSuspensions(String token) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized viewSuspensions attempt.");
            return Result.failure("Unauthorized: Invalid token.");
        }
        String adminId = authGateway.extractUserId(token);
        if (!isAdmin(token)) {
            logger.warn("Non-admin user '{}' attempted to view suspensions.", adminId);
            return Result.failure("Only admins can view suspensions.");
        }

        List<SuspensionDTO> suspensions = userRepository.findAll().stream()
                .filter(User::isSuspended)
                .map(u -> {
                    LocalDateTime start = u.getSuspendedAt();
                    LocalDateTime end = u.getSuspendedUntil();
                    Duration duration = (end != null) ? Duration.between(start, end) : null;
                    return new SuspensionDTO(u.getUsername(), start, duration, end);
                })
                .collect(Collectors.toList());

        logger.info("Admin '{}' retrieved suspension list ({} suspended users).", adminId, suspensions.size());
        return Result.success(suspensions);
    }

    // ── Event Management ──────────────────────────────────────────

    public Result<Void> cancelEventGlobally(String token, String eventId) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized cancelEventGlobally attempt for event '{}'.", eventId);
            return Result.failure("Unauthorized: Invalid token.");
        }
        String adminId = authGateway.extractUserId(token);
        if (!isAdmin(token)) {
            logger.warn("Non-admin user '{}' attempted to cancel event '{}'.", adminId, eventId);
            return Result.failure("Only admins can cancel events.");
        }

        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) {
            logger.warn("Admin '{}' tried to cancel non-existent event '{}'.", adminId, eventId);
            return Result.failure("Event not found.");
        }

        Event event = eventOpt.get();
        String eventTitle = event.getTitle();

        // Receipts that include at least one ticket for the cancelled event.
        List<OrderHistory> affectedReceipts = historyRepository.findAll().stream()
                .filter(h -> h.getItems().stream().anyMatch(i -> i.getEventId().equals(eventId)))
                .collect(Collectors.toList());
        List<String> buyerIds = affectedReceipts.stream()
                .map(OrderHistory::getUserId)
                .distinct()
                .collect(Collectors.toList());

        event.unpublish();
        eventRepository.save(event);
        eventPublisher.publishEvent(new EventCancelledEvent(eventId, eventTitle, buyerIds));

        // Integrity rule (§2 / I.3): an event cancellation must auto-refund affected buyers.
        // The payment was a single transaction per receipt, so we refund the whole transaction
        // for each affected receipt and notify the buyer.
        for (OrderHistory receipt : affectedReceipts) {
            String txnId = receipt.getTransactionId();
            if (txnId == null || txnId.isBlank()) {
                logger.warn("Cannot refund receipt '{}' for cancelled event '{}': no transaction id stored.",
                        receipt.getReceiptId(), eventId);
                continue;
            }
            Result<Void> refundResult = paymentGateway.refundPayment(txnId);
            if (refundResult.isSuccess()) {
                eventPublisher.publishEvent(new RefundIssuedEvent(
                        receipt.getUserId(), receipt.getReceiptId(), receipt.getTotalPaid(), eventTitle));
                systemLogService.logEvent("refundIssued adminId=" + adminId + " receiptId=" + receipt.getReceiptId()
                        + " transactionId=" + txnId + " amount=" + receipt.getTotalPaid());
                logger.info("Refunded receipt '{}' (transaction '{}', amount {}) for cancelled event '{}'.",
                        receipt.getReceiptId(), txnId, receipt.getTotalPaid(), eventId);
            } else {
                logger.error("Refund failed for receipt '{}' (transaction '{}') on cancelled event '{}': {}",
                        receipt.getReceiptId(), txnId, eventId, refundResult.getErrorMessage());
            }
        }

        systemLogService.logEvent("cancelEventGlobally adminId=" + adminId + " eventId=" + eventId);
        logger.warn("Admin '{}' cancelled event '{}'.", adminId, eventId);
        return Result.success();
    }

    // ── Company Management ────────────────────────────────────────

    public Result<Void> closeCompanyGlobally(String token, String companyId) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized closeCompanyGlobally attempt for company '{}'.", companyId);
            return Result.failure("Unauthorized: Invalid token.");
        }
        String adminId = authGateway.extractUserId(token);
        if (!isAdmin(token)) {
            logger.warn("Non-admin user '{}' attempted to force-close company '{}'.", adminId, companyId);
            return Result.failure("Only admins can force-close companies.");
        }

        Optional<ProductionCompany> companyOpt = companyRepository.findById(companyId);
        if (companyOpt.isEmpty()) {
            logger.warn("Admin '{}' tried to close non-existent company '{}'.", adminId, companyId);
            return Result.failure("Company not found.");
        }

        ProductionCompany company = companyOpt.get();
        List<String> staffIds = company.getStaff().values().stream()
                .map(CompanyStaffMember::getUserId)
                .collect(Collectors.toList());
        company.forceClose();
        companyRepository.save(company);
        eventPublisher.publishEvent(new CompanyClosedByAdminEvent(companyId, adminId, staffIds));
        systemLogService.logEvent("closeCompanyGlobally adminId=" + adminId + " companyId=" + companyId);
        logger.warn("Admin '{}' force-closed company '{}'.", adminId, companyId);
        return Result.success();
    }

    // ── Queue Control ─────────────────────────────────────────────

    public Result<List<TicketQueue>> viewAllQueues(String token) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized viewAllQueues attempt.");
            return Result.failure("Unauthorized.");
        }
        String adminId = authGateway.extractUserId(token);
        if (!isAdmin(token)) {
            logger.warn("Non-admin user '{}' attempted to view all queues.", adminId);
            return Result.failure("Only admins can view all queues.");
        }
        List<TicketQueue> queues = queueRepository.findAll();
        logger.info("Admin '{}' retrieved all queues ({} total).", adminId, queues.size());
        return Result.success(queues);
    }

    public Result<Void> clearEventQueue(String token, String eventId) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized clearEventQueue attempt for event '{}'.", eventId);
            return Result.failure("Unauthorized.");
        }
        String adminId = authGateway.extractUserId(token);
        if (!isAdmin(token)) {
            logger.warn("Non-admin user '{}' attempted to clear queue for event '{}'.", adminId, eventId);
            return Result.failure("Only admins can clear queues.");
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

    public Result<Void> adjustQueueRate(String token, String eventId, int newMaxConcurrentUsers) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized adjustQueueRate attempt for event '{}'.", eventId);
            return Result.failure("Unauthorized.");
        }
        String adminId = authGateway.extractUserId(token);
        if (!isAdmin(token)) {
            logger.warn("Non-admin user '{}' attempted to adjust queue rate for event '{}'.", adminId, eventId);
            return Result.failure("Only admins can adjust queue rate.");
        }

        Optional<TicketQueue> queueOpt = queueRepository.findByEventId(eventId);
        if (queueOpt.isEmpty()) {
            logger.warn("Admin '{}' tried to adjust queue rate for event '{}' but no queue exists.", adminId, eventId);
            return Result.failure("No queue found for event " + eventId);
        }

        try {
            TicketQueue queue = queueOpt.get();
            queue.adjustMaxConcurrentUsers(newMaxConcurrentUsers);
            queueRepository.save(queue);
            logger.info("Admin '{}' adjusted queue capacity for event '{}' to {}.", adminId, eventId, newMaxConcurrentUsers);
            return Result.success();
        } catch (IllegalArgumentException e) {
            logger.error("Admin '{}' supplied invalid queue capacity {} for event '{}': {}",
                    adminId, newMaxConcurrentUsers, eventId, e.getMessage());
            return Result.failure(e.getMessage());
        }
    }

    // ── Analytics ─────────────────────────────────────────────────

    public Result<SystemAnalyticsDTO> getSystemAnalytics(String token) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized getSystemAnalytics attempt.");
            return Result.failure("Unauthorized.");
        }
        String adminId = authGateway.extractUserId(token);
        if (!isAdmin(token)) {
            logger.warn("Non-admin user '{}' attempted to view system analytics.", adminId);
            return Result.failure("Only admins can view system analytics.");
        }

        int totalUsers = userRepository.findAll().size();
        int activeQueues = queueRepository.findAll().size();
        int activeCompanies = (int) companyRepository.findAll().stream()
                .filter(c -> c.getStatus().name().equals("ACTIVE"))
                .count();
        int publishedEvents = eventRepository.findPublished().size();

        logger.info("Admin '{}' retrieved system analytics: users={}, queues={}, companies={}, events={}.",
                adminId, totalUsers, activeQueues, activeCompanies, publishedEvents);
        return Result.success(new SystemAnalyticsDTO(totalUsers, activeQueues, activeCompanies, publishedEvents));
    }

    public Result<List<OrderHistoryDTO>> viewGlobalPurchaseHistory(String token) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized viewGlobalPurchaseHistory attempt.");
            return Result.failure("Unauthorized.");
        }
        String adminId = authGateway.extractUserId(token);
        if (!isAdmin(token)) {
            logger.warn("Non-admin user '{}' attempted to view global purchase history.", adminId);
            return Result.failure("Only admins can view global purchase history.");
        }

        List<OrderHistoryDTO> dtos = historyRepository.findAll().stream()
                .map(h -> new OrderHistoryDTO(
                        h.getReceiptId(), h.getUserId(), h.getPurchaseDate(), h.getTotalPaid(),
                        h.getItems().stream()
                                .map(i -> new OrderHistoryItemDTO(i.getEventId(), i.getEventTitle(),
                                        i.getEventDate(), i.getCompanyName(), i.getZoneName(),
                                        i.getSeatLabel(), i.getPricePaid()))
                                .collect(Collectors.toList())))
                .collect(Collectors.toList());
        logger.info("Admin '{}' retrieved global purchase history ({} orders).", adminId, dtos.size());
        return Result.success(dtos);
    }

    // ── Logs (SLR-8) ─────────────────────────────────────────────

    public Result<List<String>> getEventLog(String token) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized getEventLog attempt.");
            return Result.failure("Unauthorized.");
        }
        String adminId = authGateway.extractUserId(token);
        if (!isAdmin(token)) {
            logger.warn("Non-admin user '{}' attempted to view event logs.", adminId);
            return Result.failure("Only admins can view event logs.");
        }
        List<String> log = systemLogService.getEventLog();
        logger.info("Admin '{}' retrieved event log ({} entries).", adminId, log.size());
        return Result.success(log);
    }

    public Result<List<String>> getErrorLog(String token) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized getErrorLog attempt.");
            return Result.failure("Unauthorized.");
        }
        String adminId = authGateway.extractUserId(token);
        if (!isAdmin(token)) {
            logger.warn("Non-admin user '{}' attempted to view error logs.", adminId);
            return Result.failure("Only admins can view error logs.");
        }
        List<String> log = systemLogService.getErrorLog();
        logger.info("Admin '{}' retrieved error log ({} entries).", adminId, log.size());
        return Result.success(log);
    }

    // ── Messaging ─────────────────────────────────────────────────

    public Result<Void> sendMessageToUser(String token, String targetUsername, String message) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized sendMessageToUser attempt for target '{}'.", targetUsername);
            return Result.failure("Unauthorized: Invalid token.");
        }
        String adminId = authGateway.extractUserId(token);
        if (!isAdmin(token)) {
            logger.warn("Non-admin user '{}' attempted to send a message to '{}'.", adminId, targetUsername);
            return Result.failure("Only admins can send system messages.");
        }
        if (message == null || message.isBlank()) {
            return Result.failure("Message cannot be empty.");
        }
        Optional<User> targetOpt = userRepository.findByUsername(targetUsername);
        if (targetOpt.isEmpty()) {
            logger.warn("sendMessageToUser: target user '{}' not found.", targetUsername);
            return Result.failure("Target user not found.");
        }
        eventPublisher.publishEvent(new AdminMessageEvent(targetOpt.get().getId(), adminId, message));
        logger.info("Admin '{}' sent message to user '{}'.", adminId, targetUsername);
        return Result.success();
    }

    // ── Private helpers ───────────────────────────────────────────

    private boolean isAdmin(String token) {
        String userId = authGateway.extractUserId(token);
        return adminRepository.findByUserId(userId).isPresent()
                && userRepository.findById(userId).map(com.sadna.group13a.domain.Aggregates.User.User::isActive).orElse(false);
    }
}
