package com.sadna.group13a.application.Services;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.sadna.group13a.application.DTO.OrderHistoryDTO;
import com.sadna.group13a.application.DTO.SalesReportDTO;
import com.sadna.group13a.domain.shared.DiscountPolicy;
import com.sadna.group13a.domain.shared.PurchasePolicy;
import com.sadna.group13a.domain.Aggregates.Company.CompanyStaffMember;
import com.sadna.group13a.domain.Aggregates.OrderHistory.OrderHistory;
import com.sadna.group13a.domain.Interfaces.IOrderHistoryRepository;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.domain.Interfaces.ICompanyRepository;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.domain.Aggregates.User.Member;
import com.sadna.group13a.domain.Aggregates.User.User;
import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import com.sadna.group13a.domain.Aggregates.Company.CompanyPermission;
import com.sadna.group13a.domain.Aggregates.Company.CompanyRole;
import com.sadna.group13a.application.DTO.CompanyDTO;
import com.sadna.group13a.application.DTO.StaffMemberDTO;
import com.sadna.group13a.domain.Events.CompanyReopenedEvent;
import com.sadna.group13a.domain.Events.CompanySuspendedEvent;
import com.sadna.group13a.domain.Events.PermissionsUpdatedEvent;
import com.sadna.group13a.domain.Events.StaffNominatedEvent;
import com.sadna.group13a.domain.Events.StaffRemovedEvent;

@Service
public class CompanyService {

    private static final Logger logger = LoggerFactory.getLogger(CompanyService.class);

    private final ICompanyRepository companyRepository;
    private final IUserRepository userRepository;
    private final IOrderHistoryRepository historyRepository;
    private final IAuth authGateway;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    public CompanyService(ICompanyRepository companyRepository, IUserRepository userRepository,
                          IOrderHistoryRepository historyRepository,
                          IAuth authGateway, ObjectMapper objectMapper,
                          ApplicationEventPublisher eventPublisher) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.historyRepository = historyRepository;
        this.authGateway = authGateway;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    public Result<Boolean> createCompany(String token, String name, String description) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized attempt to create company '{}'.", name);
            return Result.failure("User not authenticated.");
        }
        String founderId = authGateway.extractUserId(token);
        Optional<User> founderOpt = userRepository.findById(founderId);
        if (founderOpt.isEmpty() || !founderOpt.get().canPurchase()) {
            logger.warn("createCompany failed: founder '{}' not found or inactive.", founderId);
            return Result.failure("Founder not found or inactive.");
        }

        boolean nameExists = companyRepository.findAll().stream().anyMatch(c -> c.getName().equalsIgnoreCase(name));
        if (nameExists) {
            logger.warn("createCompany failed: company name '{}' already exists.", name);
            return Result.failure("Company name already exists");
        }

        ProductionCompany company = new ProductionCompany(UUID.randomUUID().toString(), name, description, founderId);
        companyRepository.save(company);

        if (founderOpt.get() instanceof Member m) {
            m.addCompanyRole(company.getId(), CompanyRole.FOUNDER, null);
            userRepository.save(m);
        }
        logger.info("Company '{}' (id='{}') created by founder '{}'.", name, company.getId(), founderId);
        return Result.success();
    }

    public Result<Void> appointManager(String token, String companyId, String targetUsername,
                                       Set<CompanyPermission> permissions) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized attempt to appoint manager in company '{}'.", companyId);
            return Result.failure("User not authenticated.");
        }
        String initiatorId = authGateway.extractUserId(token);
        Optional<ProductionCompany> compOpt = companyRepository.findById(companyId);
        if (compOpt.isEmpty()) {
            logger.warn("appointManager failed: company '{}' not found.", companyId);
            return Result.failure("Company not found");
        }

        Optional<User> targetOpt = userRepository.findByUsername(targetUsername);
        if (targetOpt.isEmpty()) {
            logger.warn("appointManager failed: target user '{}' not found.", targetUsername);
            return Result.failure("Target user not found or inactive.");
        }

        String targetUserId = targetOpt.get().getId();
        ProductionCompany company = compOpt.get();
        try {
            company.nominateStaff(initiatorId, targetUserId, CompanyRole.MANAGER, permissions);
            companyRepository.save(company);
            eventPublisher.publishEvent(
                    new StaffNominatedEvent(targetUserId, companyId, CompanyRole.MANAGER, initiatorId));
            logger.info("User '{}' nominated '{}' as manager of company '{}'. Awaiting confirmation.",
                    initiatorId, targetUserId, companyId);
            return Result.success();
        } catch (Exception e) {
            logger.warn("appointManager failed for initiator '{}' in company '{}': {}",
                    initiatorId, companyId, e.getMessage());
            return Result.failure(e.getMessage());
        }
    }

    public Result<CompanyDTO> getCompany(String token, String companyId) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized attempt to get details for company '{}'.", companyId);
            return Result.failure("User not authenticated.");
        }
        String requesterId = authGateway.extractUserId(token);
        Optional<ProductionCompany> compOpt = companyRepository.findById(companyId);
        if (compOpt.isEmpty()) {
            logger.warn("getCompany failed: company '{}' not found (requester='{}').", companyId, requesterId);
            return Result.failure("Company not found");
        }

        ProductionCompany company = compOpt.get();
        var staffDTOs = company.getStaff().values().stream()
                .map(s -> new StaffMemberDTO(s.getUserId(), s.getRole(), s.getPermissions()))
                .toList();
        String founderId = company.getStaff().values().stream()
                .filter(s -> s.getRole() == CompanyRole.FOUNDER)
                .findFirst().map(CompanyStaffMember::getUserId).orElse("");
        logger.debug("User '{}' retrieved details for company '{}'.", requesterId, companyId);
        return Result.success(new CompanyDTO(company.getId(), company.getName(), company.getDescription(),
                company.getStatus(), founderId, staffDTOs));
    }

    public Result<Void> suspendCompany(String token, String companyId) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized attempt to suspend company '{}'.", companyId);
            return Result.failure("User not authenticated.");
        }
        String actingUserId = authGateway.extractUserId(token);
        Optional<ProductionCompany> compOpt = companyRepository.findById(companyId);
        if (compOpt.isEmpty()) {
            logger.warn("suspendCompany failed: company '{}' not found (actor='{}').", companyId, actingUserId);
            return Result.failure("Company not found");
        }
        try {
            ProductionCompany company = compOpt.get();
            List<String> staffIds = staffUserIds(company);
            company.suspendCompany(actingUserId);
            companyRepository.save(company);
            eventPublisher.publishEvent(new CompanySuspendedEvent(companyId, actingUserId, staffIds));
            logger.warn("User '{}' suspended company '{}'.", actingUserId, companyId);
            return Result.success();
        } catch (Exception e) {
            logger.warn("suspendCompany failed for user '{}' on company '{}': {}",
                    actingUserId, companyId, e.getMessage());
            return Result.failure(e.getMessage());
        }
    }

    public Result<Void> reopenCompany(String token, String companyId) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized attempt to reopen company '{}'.", companyId);
            return Result.failure("User not authenticated.");
        }
        String actingUserId = authGateway.extractUserId(token);
        Optional<ProductionCompany> compOpt = companyRepository.findById(companyId);
        if (compOpt.isEmpty()) {
            logger.warn("reopenCompany failed: company '{}' not found (actor='{}').", companyId, actingUserId);
            return Result.failure("Company not found");
        }
        try {
            ProductionCompany company = compOpt.get();
            List<String> staffIds = staffUserIds(company);
            company.reopenCompany(actingUserId);
            companyRepository.save(company);
            eventPublisher.publishEvent(new CompanyReopenedEvent(companyId, actingUserId, staffIds));
            logger.info("User '{}' reopened company '{}'.", actingUserId, companyId);
            return Result.success();
        } catch (Exception e) {
            logger.warn("reopenCompany failed for user '{}' on company '{}': {}",
                    actingUserId, companyId, e.getMessage());
            return Result.failure(e.getMessage());
        }
    }

    public Result<List<StaffMemberDTO>> getRoleTree(String token, String companyId) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized attempt to get role tree for company '{}'.", companyId);
            return Result.failure("User not authenticated.");
        }
        String actingUserId = authGateway.extractUserId(token);
        Optional<ProductionCompany> compOpt = companyRepository.findById(companyId);
        if (compOpt.isEmpty()) {
            logger.warn("getRoleTree failed: company '{}' not found (requester='{}').", companyId, actingUserId);
            return Result.failure("Company not found");
        }
        try {
            var dtos = compOpt.get().getRoleTree(actingUserId).values().stream()
                    .map(s -> new StaffMemberDTO(s.getUserId(), s.getRole(), s.getPermissions()))
                    .collect(Collectors.toList());
            logger.debug("User '{}' retrieved role tree for company '{}' ({} member(s)).",
                    actingUserId, companyId, dtos.size());
            return Result.success(dtos);
        } catch (Exception e) {
            logger.error("getRoleTree failed for company '{}' by user '{}': {}",
                    companyId, actingUserId, e.getMessage(), e);
            return Result.failure(e.getMessage());
        }
    }

    public Result<Void> fireManager(String token, String companyId, String targetUsername) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized attempt to fire manager '{}' from company '{}'.", targetUsername, companyId);
            return Result.failure("User not authenticated.");
        }
        String actingUserId = authGateway.extractUserId(token);
        Optional<User> targetOpt = userRepository.findByUsername(targetUsername);
        if (targetOpt.isEmpty()) {
            logger.warn("fireManager failed: target user '{}' not found.", targetUsername);
            return Result.failure("Target user not found or inactive.");
        }

        String targetUserId = targetOpt.get().getId();
        Optional<ProductionCompany> compOpt = companyRepository.findById(companyId);
        if (compOpt.isEmpty()) {
            logger.warn("fireManager failed: company '{}' not found (actor='{}').", companyId, actingUserId);
            return Result.failure("Company not found");
        }
        try {
            ProductionCompany company = compOpt.get();
            Set<String> subtree = company.getStaffSubTree(targetUserId);
            company.fireStaff(actingUserId, targetUserId);
            for (String uid : subtree) {
                if (!uid.equals(targetUserId) && company.getStaff().containsKey(uid)) {
                    company.fireStaff(actingUserId, uid);
                }
            }
            companyRepository.save(company);
            removeRolesForSubtree(subtree, companyId);

            List<String> allRemoved = buildRemovedList(targetUserId, subtree);
            eventPublisher.publishEvent(new StaffRemovedEvent(allRemoved, companyId, actingUserId));
            logger.warn("User '{}' fired manager '{}' from company '{}' (cascade removed {} subtree member(s)).",
                    actingUserId, targetUserId, companyId, subtree.size() - 1);
            return Result.success();
        } catch (Exception e) {
            logger.warn("fireManager failed for actor '{}' targeting '{}' in company '{}': {}",
                    actingUserId, targetUserId, companyId, e.getMessage());
            return Result.failure(e.getMessage());
        }
    }

    public Result<Void> removeOwner(String token, String companyId, String targetUsername) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized attempt to remove owner '{}' from company '{}'.", targetUsername, companyId);
            return Result.failure("User not authenticated.");
        }
        String actingUserId = authGateway.extractUserId(token);

        Optional<User> targetOpt = userRepository.findByUsername(targetUsername);
        if (targetOpt.isEmpty()) {
            logger.warn("removeOwner failed: target user '{}' not found.", targetUsername);
            return Result.failure("Target user not found.");
        }

        String targetUserId = targetOpt.get().getId();
        Optional<ProductionCompany> compOpt = companyRepository.findById(companyId);
        if (compOpt.isEmpty()) {
            logger.warn("removeOwner failed: company '{}' not found (actor='{}').", companyId, actingUserId);
            return Result.failure("Company not found");
        }

        ProductionCompany company = compOpt.get();
        CompanyStaffMember target = company.getStaff().get(targetUserId);
        if (target == null) {
            logger.warn("removeOwner failed: user '{}' is not in company '{}'.", targetUserId, companyId);
            return Result.failure("User is not in this company.");
        }
        if (target.getRole() != CompanyRole.OWNER) {
            logger.warn("removeOwner failed: user '{}' in company '{}' has role {} (not OWNER).",
                    targetUserId, companyId, target.getRole());
            return Result.failure("Target is not an Owner.");
        }

        try {
            Set<String> subtree = company.getStaffSubTree(targetUserId);
            company.fireStaff(actingUserId, targetUserId);
            companyRepository.save(company);
            removeRolesForSubtree(subtree, companyId);

            List<String> allRemoved = buildRemovedList(targetUserId, subtree);
            eventPublisher.publishEvent(new StaffRemovedEvent(allRemoved, companyId, actingUserId));
            logger.warn("User '{}' removed owner '{}' from company '{}'.", actingUserId, targetUserId, companyId);
            return Result.success();
        } catch (Exception e) {
            logger.warn("removeOwner failed for actor '{}' targeting '{}' in company '{}': {}",
                    actingUserId, targetUserId, companyId, e.getMessage());
            return Result.failure(e.getMessage());
        }
    }

    public Result<Void> resign(String token, String companyId) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized attempt to resign from company '{}'.", companyId);
            return Result.failure("User not authenticated.");
        }
        String actingUserId = authGateway.extractUserId(token);
        Optional<ProductionCompany> compOpt = companyRepository.findById(companyId);
        if (compOpt.isEmpty()) {
            logger.warn("resign failed: company '{}' not found (actor='{}').", companyId, actingUserId);
            return Result.failure("Company not found");
        }
        try {
            ProductionCompany company = compOpt.get();
            Set<String> subtree = company.getStaffSubTree(actingUserId);
            company.resign(actingUserId);
            companyRepository.save(company);
            removeRolesForSubtree(subtree, companyId);

            List<String> allRemoved = buildRemovedList(actingUserId, subtree);
            eventPublisher.publishEvent(new StaffRemovedEvent(allRemoved, companyId, actingUserId));
            logger.info("User '{}' resigned from company '{}'.", actingUserId, companyId);
            return Result.success();
        } catch (Exception e) {
            logger.warn("resign failed for user '{}' in company '{}': {}",
                    actingUserId, companyId, e.getMessage());
            return Result.failure(e.getMessage());
        }
    }

    public Result<Void> appointOwner(String token, String companyId, String targetUsername) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized attempt to appoint owner in company '{}'.", companyId);
            return Result.failure("User not authenticated.");
        }
        String initiatorId = authGateway.extractUserId(token);
        Optional<ProductionCompany> compOpt = companyRepository.findById(companyId);
        if (compOpt.isEmpty()) {
            logger.warn("appointOwner failed: company '{}' not found.", companyId);
            return Result.failure("Company not found");
        }

        Optional<User> targetOpt = userRepository.findByUsername(targetUsername);
        if (targetOpt.isEmpty()) {
            logger.warn("appointOwner failed: target user '{}' not found.", targetUsername);
            return Result.failure("Target user not found.");
        }

        String targetUserId = targetOpt.get().getId();
        ProductionCompany company = compOpt.get();
        try {
            company.nominateStaff(initiatorId, targetUserId, CompanyRole.OWNER, null);
            companyRepository.save(company);
            eventPublisher.publishEvent(
                    new StaffNominatedEvent(targetUserId, companyId, CompanyRole.OWNER, initiatorId));
            logger.info("User '{}' nominated '{}' as owner of company '{}'. Awaiting confirmation.",
                    initiatorId, targetUserId, companyId);
            return Result.success();
        } catch (Exception e) {
            logger.warn("appointOwner failed for initiator '{}' in company '{}': {}",
                    initiatorId, companyId, e.getMessage());
            return Result.failure(e.getMessage());
        }
    }

    public Result<Void> acceptNomination(String token, String companyId) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized attempt to accept nomination in company '{}'.", companyId);
            return Result.failure("User not authenticated.");
        }
        String nomineeId = authGateway.extractUserId(token);

        Optional<User> userOpt = userRepository.findById(nomineeId);
        if (userOpt.isEmpty()) {
            logger.warn("acceptNomination failed: user '{}' not found.", nomineeId);
            return Result.failure("User not found.");
        }

        Optional<ProductionCompany> compOpt = companyRepository.findById(companyId);
        if (compOpt.isEmpty()) {
            logger.warn("acceptNomination failed: company '{}' not found (nominee='{}').", companyId, nomineeId);
            return Result.failure("Company not found.");
        }

        ProductionCompany company = compOpt.get();
        try {
            var pending = company.getPendingAppointments().get(nomineeId);
            if (pending == null) {
                logger.warn("acceptNomination failed: no pending nomination for user '{}' in company '{}'.",
                        nomineeId, companyId);
                return Result.failure("No pending nomination found.");
            }
            CompanyRole assignedRole = pending.getProposedRole();
            String appointerId = pending.getAppointerId();

            company.acceptNomination(nomineeId);
            companyRepository.save(company);

            if (userOpt.get() instanceof Member m) {
                m.addCompanyRole(companyId, assignedRole, appointerId);
                userRepository.save(m);
            }
            logger.info("User '{}' accepted nomination for role {} in company '{}'.",
                    nomineeId, assignedRole, companyId);
            return Result.success();
        } catch (Exception e) {
            logger.warn("acceptNomination failed for user '{}' in company '{}': {}",
                    nomineeId, companyId, e.getMessage());
            return Result.failure(e.getMessage());
        }
    }

    public Result<Void> rejectNomination(String token, String companyId) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized attempt to reject nomination in company '{}'.", companyId);
            return Result.failure("User not authenticated.");
        }
        String nomineeId = authGateway.extractUserId(token);

        Optional<ProductionCompany> compOpt = companyRepository.findById(companyId);
        if (compOpt.isEmpty()) {
            logger.warn("rejectNomination failed: company '{}' not found (nominee='{}').", companyId, nomineeId);
            return Result.failure("Company not found.");
        }

        ProductionCompany company = compOpt.get();
        try {
            company.rejectNomination(nomineeId);
            companyRepository.save(company);
            logger.info("User '{}' rejected nomination in company '{}'.", nomineeId, companyId);
            return Result.success();
        } catch (Exception e) {
            logger.warn("rejectNomination failed for user '{}' in company '{}': {}",
                    nomineeId, companyId, e.getMessage());
            return Result.failure(e.getMessage());
        }
    }

    public Result<List<OrderHistoryDTO>> viewCompanyOrders(String token, String companyId) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized attempt to view orders for company '{}'.", companyId);
            return Result.failure("User not authenticated.");
        }
        String actingUserId = authGateway.extractUserId(token);
        Optional<ProductionCompany> compOpt = companyRepository.findById(companyId);
        if (compOpt.isEmpty()) {
            logger.warn("viewCompanyOrders failed: company '{}' not found (requester='{}').", companyId, actingUserId);
            return Result.failure("Company not found");
        }
        ProductionCompany company = compOpt.get();
        if (!company.isOwner(actingUserId) && !company.hasPermission(actingUserId, CompanyPermission.VIEW_REPORTS)) {
            logger.warn("viewCompanyOrders denied: user '{}' lacks VIEW_REPORTS permission for company '{}'.",
                    actingUserId, companyId);
            return Result.failure("Permission denied: VIEW_REPORTS required.");
        }
        List<OrderHistoryDTO> dtos = historyRepository.findByCompanyId(companyId).stream()
                .map(h -> objectMapper.convertValue(h, OrderHistoryDTO.class))
                .collect(Collectors.toList());
        logger.debug("User '{}' retrieved {} order(s) for company '{}'.", actingUserId, dtos.size(), companyId);
        return Result.success(dtos);
    }

    public Result<SalesReportDTO> generateSalesReport(String token, String companyId) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized attempt to generate sales report for company '{}'.", companyId);
            return Result.failure("User not authenticated.");
        }
        String actingUserId = authGateway.extractUserId(token);
        Optional<ProductionCompany> compOpt = companyRepository.findById(companyId);
        if (compOpt.isEmpty()) {
            logger.warn("generateSalesReport failed: company '{}' not found (requester='{}').", companyId, actingUserId);
            return Result.failure("Company not found");
        }
        ProductionCompany company = compOpt.get();
        if (!company.isOwner(actingUserId) && !company.hasPermission(actingUserId, CompanyPermission.VIEW_REPORTS)) {
            logger.warn("generateSalesReport denied: user '{}' lacks VIEW_REPORTS permission for company '{}'.",
                    actingUserId, companyId);
            return Result.failure("Permission denied: VIEW_REPORTS required.");
        }
        List<OrderHistory> orders = historyRepository.findByCompanyId(companyId);
        double totalRevenue = orders.stream()
                .flatMap(h -> h.getItems().stream())
                .mapToDouble(i -> i.getPricePaid())
                .sum();
        List<OrderHistoryDTO> dtos = orders.stream()
                .map(h -> objectMapper.convertValue(h, OrderHistoryDTO.class))
                .collect(Collectors.toList());
        logger.info("Sales report generated for company '{}' by user '{}': {} order(s), total revenue={}.",
                companyId, actingUserId, orders.size(), totalRevenue);
        return Result.success(new SalesReportDTO(companyId, company.getName(), orders.size(), totalRevenue, dtos));
    }

    public Result<Void> updatePermissions(String token, String companyId, String targetManagerUsername,
                                          Set<CompanyPermission> permissions) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized attempt to update permissions for '{}' in company '{}'.",
                    targetManagerUsername, companyId);
            return Result.failure("User not authenticated.");
        }
        String actingUserId = authGateway.extractUserId(token);
        Optional<User> targetOpt = userRepository.findByUsername(targetManagerUsername);
        if (targetOpt.isEmpty()) {
            logger.warn("updatePermissions failed: target user '{}' not found.", targetManagerUsername);
            return Result.failure("Target user not found or inactive.");
        }

        String targetManagerId = targetOpt.get().getId();
        Optional<ProductionCompany> compOpt = companyRepository.findById(companyId);
        if (compOpt.isEmpty()) {
            logger.warn("updatePermissions failed: company '{}' not found (actor='{}').", companyId, actingUserId);
            return Result.failure("Company not found");
        }
        try {
            ProductionCompany company = compOpt.get();
            company.updatePermissions(actingUserId, targetManagerId, permissions);
            companyRepository.save(company);
            eventPublisher.publishEvent(new PermissionsUpdatedEvent(targetManagerId, companyId));
            logger.info("User '{}' updated permissions for '{}' in company '{}': {}.",
                    actingUserId, targetManagerId, companyId, permissions);
            return Result.success();
        } catch (Exception e) {
            logger.warn("updatePermissions failed for actor '{}' targeting '{}' in company '{}': {}",
                    actingUserId, targetManagerId, companyId, e.getMessage());
            return Result.failure(e.getMessage());
        }
    }

    // ── Policy Management (11.6-related) ─────────────────────────

    /**
     * Replaces the purchase policy root for a company.
     * Caller must be a Founder or Owner of the company.
     */
    public Result<Void> setPurchasePolicy(String token, String companyId, PurchasePolicy policy) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized setPurchasePolicy attempt for company '{}'.", companyId);
            return Result.failure("User not authenticated.");
        }
        String actingUserId = authGateway.extractUserId(token);
        Optional<ProductionCompany> compOpt = companyRepository.findById(companyId);
        if (compOpt.isEmpty()) {
            logger.warn("setPurchasePolicy failed: company '{}' not found.", companyId);
            return Result.failure("Company not found.");
        }
        ProductionCompany company = compOpt.get();
        if (!company.isOwner(actingUserId)) {
            logger.warn("setPurchasePolicy denied: user '{}' is not an owner of company '{}'.", actingUserId, companyId);
            return Result.failure("Only founders and owners can change the purchase policy.");
        }
        try {
            company.setPurchasePolicy(policy);
            companyRepository.save(company);
            logger.info("User '{}' updated purchase policy for company '{}'.", actingUserId, companyId);
            return Result.success();
        } catch (Exception e) {
            logger.warn("setPurchasePolicy failed for company '{}': {}", companyId, e.getMessage());
            return Result.failure(e.getMessage());
        }
    }

    /**
     * Replaces the discount policy root for a company.
     * Caller must be a Founder or Owner of the company.
     */
    public Result<Void> setDiscountPolicy(String token, String companyId, DiscountPolicy policy) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized setDiscountPolicy attempt for company '{}'.", companyId);
            return Result.failure("User not authenticated.");
        }
        String actingUserId = authGateway.extractUserId(token);
        Optional<ProductionCompany> compOpt = companyRepository.findById(companyId);
        if (compOpt.isEmpty()) {
            logger.warn("setDiscountPolicy failed: company '{}' not found.", companyId);
            return Result.failure("Company not found.");
        }
        ProductionCompany company = compOpt.get();
        if (!company.isOwner(actingUserId)) {
            logger.warn("setDiscountPolicy denied: user '{}' is not an owner of company '{}'.", actingUserId, companyId);
            return Result.failure("Only founders and owners can change the discount policy.");
        }
        try {
            company.setDiscountPolicy(policy);
            companyRepository.save(company);
            logger.info("User '{}' updated discount policy for company '{}'.", actingUserId, companyId);
            return Result.success();
        } catch (Exception e) {
            logger.warn("setDiscountPolicy failed for company '{}': {}", companyId, e.getMessage());
            return Result.failure(e.getMessage());
        }
    }

    // ── Private helpers ───────────────────────────────────────────

    private void removeRolesForSubtree(Set<String> userIds, String companyId) {
        for (String uid : userIds) {
            userRepository.findById(uid).ifPresent(u -> {
                if (u instanceof Member m) {
                    m.removeCompanyRole(companyId);
                    userRepository.save(m);
                }
            });
        }
    }

    private List<String> staffUserIds(ProductionCompany company) {
        return company.getStaff().values().stream()
                .map(CompanyStaffMember::getUserId)
                .collect(Collectors.toList());
    }

    private List<String> buildRemovedList(String primaryId, Set<String> subtree) {
        List<String> all = new ArrayList<>();
        all.add(primaryId);
        subtree.stream().filter(uid -> !uid.equals(primaryId)).forEach(all::add);
        return all;
    }
}
