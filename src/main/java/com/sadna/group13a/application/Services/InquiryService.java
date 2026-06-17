package com.sadna.group13a.application.Services;

import com.sadna.group13a.application.DTO.InquiryDTO;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.domain.Aggregates.Company.CompanyStatus;
import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import com.sadna.group13a.domain.Aggregates.Inquiry.Inquiry;
import com.sadna.group13a.domain.Aggregates.User.Member;
import com.sadna.group13a.domain.Aggregates.User.User;
import com.sadna.group13a.domain.Events.InquiryAnsweredEvent;
import com.sadna.group13a.domain.Interfaces.ICompanyRepository;
import com.sadna.group13a.domain.Interfaces.IInquiryRepository;
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
 * Application service for buyer inquiries to a production company
 * (II.3.7 send; II.4.4 owner receives &amp; responds). Each use case is one transaction.
 */
@Service
public class InquiryService {

    private static final Logger logger = LoggerFactory.getLogger(InquiryService.class);

    private final IInquiryRepository inquiryRepository;
    private final ICompanyRepository companyRepository;
    private final IUserRepository userRepository;
    private final IAuth authGateway;
    private final ApplicationEventPublisher eventPublisher;
    private final SystemLogService systemLogService;

    public InquiryService(IInquiryRepository inquiryRepository,
                          ICompanyRepository companyRepository,
                          IUserRepository userRepository,
                          IAuth authGateway,
                          ApplicationEventPublisher eventPublisher,
                          SystemLogService systemLogService) {
        this.inquiryRepository = inquiryRepository;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.authGateway = authGateway;
        this.eventPublisher = eventPublisher;
        this.systemLogService = systemLogService;
    }

    /** II.3.7 — a member sends an inquiry to an active production company. */
    @Transactional
    public Result<String> submitInquiry(String token, String companyId, String message) {
        if (!authGateway.validateToken(token)) {
            return Result.failure("Unauthorized: invalid token");
        }
        if (message == null || message.isBlank()) {
            return Result.failure("An inquiry message is required.");
        }
        String userId = authGateway.extractUserId(token);
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty() || !(userOpt.get() instanceof Member) || !userOpt.get().isActive()) {
            logger.warn("submitInquiry: user '{}' is not an active member.", userId);
            return Result.failure("Only registered members can send inquiries.");
        }
        Optional<ProductionCompany> companyOpt = companyRepository.findById(companyId);
        if (companyOpt.isEmpty() || companyOpt.get().getStatus() != CompanyStatus.ACTIVE) {
            return Result.failure("Company is not available.");
        }

        Inquiry inquiry = new Inquiry(
                UUID.randomUUID().toString(), userId, companyId, message.trim(), LocalDateTime.now());
        inquiryRepository.save(inquiry);
        systemLogService.logEvent("Inquiry sent by user " + userId + " to company " + companyId
                + " (id " + inquiry.getId() + ").");
        logger.info("User '{}' sent inquiry '{}' to company '{}'.", userId, inquiry.getId(), companyId);
        return Result.success(inquiry.getId());
    }

    /** A member views the inquiries they sent. */
    @Transactional(readOnly = true)
    public Result<List<InquiryDTO>> getMyInquiries(String token) {
        if (!authGateway.validateToken(token)) {
            return Result.failure("Unauthorized: invalid token");
        }
        String userId = authGateway.extractUserId(token);
        List<InquiryDTO> dtos = inquiryRepository.findByFromUserId(userId).stream()
                .sorted(Comparator.comparing(Inquiry::getCreatedAt).reversed())
                .map(this::toDTO)
                .toList();
        return Result.success(dtos);
    }

    /** II.4.4 — an owner of the company views inquiries addressed to it. */
    @Transactional(readOnly = true)
    public Result<List<InquiryDTO>> getCompanyInquiries(String token, String companyId) {
        if (!authGateway.validateToken(token)) {
            return Result.failure("Unauthorized: invalid token");
        }
        String userId = authGateway.extractUserId(token);
        Optional<ProductionCompany> companyOpt = companyRepository.findById(companyId);
        if (companyOpt.isEmpty()) {
            return Result.failure("Company not found.");
        }
        if (!companyOpt.get().isOwner(userId)) {
            logger.warn("User '{}' attempted to view inquiries for company '{}' without ownership.", userId, companyId);
            return Result.failure("Only company owners can view inquiries.");
        }
        List<InquiryDTO> dtos = inquiryRepository.findByCompanyId(companyId).stream()
                .sorted(Comparator.comparing(Inquiry::getCreatedAt).reversed())
                .map(this::toDTO)
                .toList();
        return Result.success(dtos);
    }

    /** II.4.4 — an owner answers an inquiry; the sender is notified (I.5 / I.6). */
    @Transactional
    public Result<Void> respondToInquiry(String token, String inquiryId, String response) {
        if (!authGateway.validateToken(token)) {
            return Result.failure("Unauthorized: invalid token");
        }
        String userId = authGateway.extractUserId(token);
        if (response == null || response.isBlank()) {
            return Result.failure("A response cannot be empty.");
        }
        Optional<Inquiry> inquiryOpt = inquiryRepository.findById(inquiryId);
        if (inquiryOpt.isEmpty()) {
            return Result.failure("Inquiry not found.");
        }
        Inquiry inquiry = inquiryOpt.get();
        Optional<ProductionCompany> companyOpt = companyRepository.findById(inquiry.getCompanyId());
        if (companyOpt.isEmpty()) {
            return Result.failure("Company not found.");
        }
        ProductionCompany company = companyOpt.get();
        if (!company.isOwner(userId)) {
            logger.warn("User '{}' attempted to answer inquiry '{}' without ownership.", userId, inquiryId);
            return Result.failure("Only company owners can answer inquiries.");
        }
        try {
            inquiry.answer(userId, response.trim());
        } catch (IllegalStateException | IllegalArgumentException e) {
            return Result.failure(e.getMessage());
        }
        inquiryRepository.save(inquiry);

        eventPublisher.publishEvent(new InquiryAnsweredEvent(
                inquiry.getFromUserId(), company.getName(), inquiry.getResponse()));
        systemLogService.logEvent("Inquiry " + inquiryId + " answered by owner " + userId
                + " of company " + company.getName() + ".");
        logger.info("User '{}' answered inquiry '{}'.", userId, inquiryId);
        return Result.success();
    }

    // ── Helpers ───────────────────────────────────────────────────

    private InquiryDTO toDTO(Inquiry i) {
        String username = userRepository.findById(i.getFromUserId())
                .map(User::getUsername).orElse("(unknown)");
        return new InquiryDTO(
                i.getId(), username, i.getCompanyId(), i.getMessage(), i.getCreatedAt(),
                i.getStatus().name(), i.getResponse(), i.getRespondedAt());
    }
}
