package com.sadna.group13a.application.Services;

import com.sadna.group13a.application.DTO.ComplaintDTO;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.domain.Aggregates.Complaint.Complaint;
import com.sadna.group13a.domain.Aggregates.User.Member;
import com.sadna.group13a.domain.Aggregates.User.User;
import com.sadna.group13a.domain.Events.AdminMessageEvent;
import com.sadna.group13a.domain.Interfaces.IAdminRepository;
import com.sadna.group13a.domain.Interfaces.IComplaintRepository;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service for buyer complaints to the platform administrators
 * (II.3.3 submit; II.6.3 handle &amp; respond). Each use case is one transaction.
 */
@Service
public class ComplaintService {

    private static final Logger logger = LoggerFactory.getLogger(ComplaintService.class);

    private final IComplaintRepository complaintRepository;
    private final IUserRepository userRepository;
    private final IAdminRepository adminRepository;
    private final IAuth authGateway;
    private final ApplicationEventPublisher eventPublisher;
    private final SystemLogService systemLogService;

    public ComplaintService(IComplaintRepository complaintRepository,
                            IUserRepository userRepository,
                            IAdminRepository adminRepository,
                            IAuth authGateway,
                            ApplicationEventPublisher eventPublisher,
                            SystemLogService systemLogService) {
        this.complaintRepository = complaintRepository;
        this.userRepository = userRepository;
        this.adminRepository = adminRepository;
        this.authGateway = authGateway;
        this.eventPublisher = eventPublisher;
        this.systemLogService = systemLogService;
    }

    /** II.3.3 — a member submits a complaint to the administrators. */
    @Transactional
    public Result<String> submitComplaint(String token, String subject, String message) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized submitComplaint attempt.");
            return Result.failure("Unauthorized: invalid token");
        }
        if (subject == null || subject.isBlank()) {
            return Result.failure("A complaint subject is required.");
        }
        if (message == null || message.isBlank()) {
            return Result.failure("A complaint message is required.");
        }
        String userId = authGateway.extractUserId(token);
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty() || !(userOpt.get() instanceof Member) || !userOpt.get().isActive()) {
            logger.warn("submitComplaint: user '{}' is not an active member.", userId);
            return Result.failure("Only registered members can submit complaints.");
        }

        Complaint complaint = new Complaint(
                UUID.randomUUID().toString(), userId, subject.trim(), message.trim(), LocalDateTime.now());
        complaintRepository.save(complaint);
        systemLogService.logEvent("Complaint submitted by user " + userId + " (id " + complaint.getId() + ").");
        logger.info("User '{}' submitted complaint '{}'.", userId, complaint.getId());
        return Result.success(complaint.getId());
    }

    /** A member views the complaints they submitted. */
    @Transactional(readOnly = true)
    public Result<List<ComplaintDTO>> getMyComplaints(String token) {
        if (!authGateway.validateToken(token)) {
            return Result.failure("Unauthorized: invalid token");
        }
        String userId = authGateway.extractUserId(token);
        List<ComplaintDTO> dtos = complaintRepository.findByComplainantUserId(userId).stream()
                .sorted(Comparator.comparing(Complaint::getCreatedAt).reversed())
                .map(this::toDTO)
                .toList();
        return Result.success(dtos);
    }

    /** II.6.3 — an administrator lists all complaints to handle. */
    @Transactional(readOnly = true)
    public Result<List<ComplaintDTO>> getAllComplaints(String token) {
        if (!authGateway.validateToken(token)) {
            return Result.failure("Unauthorized: invalid token");
        }
        if (!isAdmin(token)) {
            logger.warn("Non-admin attempted to view all complaints.");
            return Result.failure("Only admins can view complaints.");
        }
        List<ComplaintDTO> dtos = complaintRepository.findAll().stream()
                .sorted(Comparator.comparing(Complaint::getCreatedAt).reversed())
                .map(this::toDTO)
                .toList();
        return Result.success(dtos);
    }

    /** II.6.3 — an administrator responds to and resolves a complaint; the complainant is notified. */
    @Transactional
    public Result<Void> respondToComplaint(String token, String complaintId, String response) {
        if (!authGateway.validateToken(token)) {
            return Result.failure("Unauthorized: invalid token");
        }
        String adminId = authGateway.extractUserId(token);
        if (!isAdmin(token)) {
            logger.warn("Non-admin user '{}' attempted to respond to complaint '{}'.", adminId, complaintId);
            return Result.failure("Only admins can respond to complaints.");
        }
        if (response == null || response.isBlank()) {
            return Result.failure("A response cannot be empty.");
        }
        Optional<Complaint> complaintOpt = complaintRepository.findById(complaintId);
        if (complaintOpt.isEmpty()) {
            return Result.failure("Complaint not found.");
        }
        Complaint complaint = complaintOpt.get();
        try {
            complaint.respond(adminId, response.trim());
        } catch (IllegalStateException | IllegalArgumentException e) {
            return Result.failure(e.getMessage());
        }
        complaintRepository.save(complaint);

        eventPublisher.publishEvent(new AdminMessageEvent(
                complaint.getComplainantUserId(), adminId,
                "Your complaint \"" + complaint.getSubject() + "\" was answered: " + complaint.getAdminResponse()));
        systemLogService.logEvent("Complaint " + complaintId + " resolved by admin " + adminId + ".");
        logger.info("Admin '{}' responded to complaint '{}'.", adminId, complaintId);
        return Result.success();
    }

    // ── Helpers ───────────────────────────────────────────────────

    private boolean isAdmin(String token) {
        String userId = authGateway.extractUserId(token);
        return adminRepository.findByUserId(userId).isPresent()
                && userRepository.findById(userId).map(User::isActive).orElse(false);
    }

    private ComplaintDTO toDTO(Complaint c) {
        String username = userRepository.findById(c.getComplainantUserId())
                .map(User::getUsername).orElse("(unknown)");
        return new ComplaintDTO(
                c.getId(), username, c.getSubject(), c.getMessage(), c.getCreatedAt(),
                c.getStatus().name(), c.getAdminResponse(), c.getResolvedAt());
    }
}
