package com.sadna.group13a.application.Services;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.List;
import java.util.stream.Collectors;

import com.sadna.group13a.application.DTO.OrderHistoryDTO;
import com.sadna.group13a.application.DTO.SalesReportDTO;
import com.sadna.group13a.domain.Aggregates.Company.CompanyStaffMember;
import com.sadna.group13a.domain.Aggregates.OrderHistory.OrderHistory;
import com.sadna.group13a.domain.Interfaces.IOrderHistoryRepository;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
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

@Service
public class CompanyService {

    private static final Logger logger = LoggerFactory.getLogger(CompanyService.class);

    private final ICompanyRepository companyRepository;
    private final IUserRepository userRepository;
    private final IOrderHistoryRepository historyRepository;
    private final IAuth authGateway;
    private final ObjectMapper objectMapper;

    public CompanyService(ICompanyRepository companyRepository, IUserRepository userRepository,
                          IOrderHistoryRepository historyRepository,
                          IAuth authGateway, ObjectMapper objectMapper) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.historyRepository = historyRepository;
        this.authGateway = authGateway;
        this.objectMapper = objectMapper;
    }

    public Result<Boolean> createCompany(String token, String name, String description) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized attempt to create a company.");
            return Result.failure("User not authenticated.");
        }
        String founderId = authGateway.extractUserId(token);
        Optional<User> founderOpt = userRepository.findById(founderId);
        if (founderOpt.isEmpty() || !founderOpt.get().canPurchase()) {
            return Result.failure("Founder not found or inactive.");
        }

        boolean nameExists = companyRepository.findAll().stream().anyMatch(c -> c.getName().equalsIgnoreCase(name));

        if (nameExists) return Result.failure("Company name already exists");

        ProductionCompany company = new ProductionCompany(UUID.randomUUID().toString(), name, description, founderId);
        companyRepository.save(company);

        // Update founder's role registry
        if (founderOpt.get() instanceof Member m) {
            m.addCompanyRole(company.getId(), CompanyRole.FOUNDER, null);
            userRepository.save(m);
        }
        return Result.success();
    }

    public Result<Void> appointManager(String token, String companyId, String targetUsername,
                                       Set<CompanyPermission> permissions) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized attempt to appoint a manager.");
            return Result.failure("User not authenticated.");
        }
        Optional<ProductionCompany> compOpt = companyRepository.findById(companyId);
        if (compOpt.isEmpty()) return Result.failure("Company not found");

        Optional<User> targetOpt = userRepository.findByUsername(targetUsername);
        if (targetOpt.isEmpty()) return Result.failure("Target user not found or inactive.");

        String targetUserId = targetOpt.get().getId();
        String initiatorId = authGateway.extractUserId(token);
        ProductionCompany company = compOpt.get();
        try {
            company.nominateStaff(initiatorId, targetUserId, CompanyRole.MANAGER, permissions);
            companyRepository.save(company);
            logger.info("User {} nominated {} as manager of company {}. Awaiting confirmation.", initiatorId, targetUserId, companyId);
            return Result.success();
        } catch (Exception e) {
            return Result.failure(e.getMessage());
        }
    }

    public Result<CompanyDTO> getCompany(String token, String companyId) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized attempt to get company details.");
            return Result.failure("User not authenticated.");
        }
        Optional<ProductionCompany> compOpt = companyRepository.findById(companyId);
        if (compOpt.isEmpty()) return Result.failure("Company not found");

        ProductionCompany company = compOpt.get();
        var staffDTOs = company.getStaff().values().stream()
                .map(s -> new StaffMemberDTO(s.getUserId(), s.getRole(), s.getPermissions()))
                .toList();
        String founderId = company.getStaff().values().stream()
                .filter(s -> s.getRole() == CompanyRole.FOUNDER)
                .findFirst().map(CompanyStaffMember::getUserId).orElse("");
        return Result.success(new CompanyDTO(company.getId(), company.getName(), company.getDescription(),
                company.getStatus(), founderId, staffDTOs));
    }

    public Result<Void> suspendCompany(String token, String companyId) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized attempt to suspend a company.");
            return Result.failure("User not authenticated.");
        }
        String actingUserId = authGateway.extractUserId(token);
        Optional<ProductionCompany> compOpt = companyRepository.findById(companyId);
        if (compOpt.isEmpty()) return Result.failure("Company not found");
        try {
            ProductionCompany company = compOpt.get();
            company.suspendCompany(actingUserId);
            companyRepository.save(company);
            return Result.success();
        } catch (Exception e) {
            return Result.failure(e.getMessage());
        }
    }

    public Result<Void> reopenCompany(String token, String companyId) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized attempt to reopen a company.");
            return Result.failure("User not authenticated.");
        }
        String actingUserId = authGateway.extractUserId(token);
        Optional<ProductionCompany> compOpt = companyRepository.findById(companyId);
        if (compOpt.isEmpty()) return Result.failure("Company not found");
        try {
            ProductionCompany company = compOpt.get();
            company.reopenCompany(actingUserId);
            companyRepository.save(company);
            return Result.success();
        } catch (Exception e) {
            return Result.failure(e.getMessage());
        }
    }

    public Result<List<StaffMemberDTO>> getRoleTree(String token, String companyId) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized attempt to get role tree.");
            return Result.failure("User not authenticated.");
        }
        String actingUserId = authGateway.extractUserId(token);
        Optional<ProductionCompany> compOpt = companyRepository.findById(companyId);
        if (compOpt.isEmpty()) return Result.failure("Company not found");
        try {
            var dtos = compOpt.get().getRoleTree(actingUserId).values().stream()
                    .map(s -> new StaffMemberDTO(s.getUserId(), s.getRole(), s.getPermissions()))
                    .collect(Collectors.toList());
            return Result.success(dtos);
        } catch (Exception e) {
            logger.error("Error retrieving role tree for company {} by user {}", companyId, actingUserId, e);
            return Result.failure(e.getMessage());
        }
    }

    public Result<Void> fireManager(String token, String companyId, String targetUsername) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized attempt to fire a manager.");
            return Result.failure("User not authenticated.");
        }
        String actingUserId = authGateway.extractUserId(token);
        Optional<User> targetOpt = userRepository.findByUsername(targetUsername);
        if (targetOpt.isEmpty()) return Result.failure("Target user not found or inactive.");

        String targetUserId = targetOpt.get().getId();
        Optional<ProductionCompany> compOpt = companyRepository.findById(companyId);
        if (compOpt.isEmpty()) return Result.failure("Company not found");
        try {
            ProductionCompany company = compOpt.get();
            Set<String> subtree = company.getStaffSubTree(targetUserId);
            company.fireStaff(actingUserId, targetUserId);
            // After firing, sub-appointees are re-parented to actingUserId; cascade-remove them too
            for (String uid : subtree) {
                if (!uid.equals(targetUserId) && company.getStaff().containsKey(uid)) {
                    company.fireStaff(actingUserId, uid);
                }
            }
            companyRepository.save(company);
            removeRolesForSubtree(subtree, companyId);
            return Result.success();
        } catch (Exception e) {
            return Result.failure(e.getMessage());
        }
    }

    public Result<Void> removeOwner(String token, String companyId, String targetUsername) {
        if (!authGateway.validateToken(token)) return Result.failure("User not authenticated.");
        String actingUserId = authGateway.extractUserId(token);

        Optional<User> targetOpt = userRepository.findByUsername(targetUsername);
        if (targetOpt.isEmpty()) return Result.failure("Target user not found.");

        String targetUserId = targetOpt.get().getId();
        Optional<ProductionCompany> compOpt = companyRepository.findById(companyId);
        if (compOpt.isEmpty()) return Result.failure("Company not found");

        ProductionCompany company = compOpt.get();
        CompanyStaffMember target = company.getStaff().get(targetUserId);
        if (target == null) return Result.failure("User is not in this company.");
        if (target.getRole() != CompanyRole.OWNER) return Result.failure("Target is not an Owner.");

        try {
            Set<String> subtree = company.getStaffSubTree(targetUserId);
            company.fireStaff(actingUserId, targetUserId);
            companyRepository.save(company);
            removeRolesForSubtree(subtree, companyId);
            logger.info("User {} removed owner {} from company {}.", actingUserId, targetUserId, companyId);
            return Result.success();
        } catch (Exception e) {
            return Result.failure(e.getMessage());
        }
    }

    public Result<Void> resign(String token, String companyId) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized attempt to resign from a company.");
            return Result.failure("User not authenticated.");
        }
        String actingUserId = authGateway.extractUserId(token);
        Optional<ProductionCompany> compOpt = companyRepository.findById(companyId);
        if (compOpt.isEmpty()) return Result.failure("Company not found");
        try {
            ProductionCompany company = compOpt.get();
            Set<String> subtree = company.getStaffSubTree(actingUserId);
            company.resign(actingUserId);
            companyRepository.save(company);
            removeRolesForSubtree(subtree, companyId);
            return Result.success();
        } catch (Exception e) {
            return Result.failure(e.getMessage());
        }
    }

    public Result<Void> appointOwner(String token, String companyId, String targetUsername) {
        if (!authGateway.validateToken(token)) return Result.failure("User not authenticated.");
        Optional<ProductionCompany> compOpt = companyRepository.findById(companyId);
        if (compOpt.isEmpty()) return Result.failure("Company not found");

        Optional<User> targetOpt = userRepository.findByUsername(targetUsername);
        if (targetOpt.isEmpty()) return Result.failure("Target user not found.");

        String targetUserId = targetOpt.get().getId();
        String initiatorId = authGateway.extractUserId(token);
        ProductionCompany company = compOpt.get();
        try {
            company.nominateStaff(initiatorId, targetUserId, CompanyRole.OWNER, null);
            companyRepository.save(company);
            logger.info("User {} nominated {} as owner of company {}. Awaiting confirmation.", initiatorId, targetUserId, companyId);
            return Result.success();
        } catch (Exception e) {
            return Result.failure(e.getMessage());
        }
    }

    public Result<Void> acceptNomination(String token, String companyId) {
        if (!authGateway.validateToken(token)) return Result.failure("User not authenticated.");
        String nomineeId = authGateway.extractUserId(token);

        Optional<User> userOpt = userRepository.findById(nomineeId);
        if (userOpt.isEmpty()) return Result.failure("User not found.");

        Optional<ProductionCompany> compOpt = companyRepository.findById(companyId);
        if (compOpt.isEmpty()) return Result.failure("Company not found.");

        ProductionCompany company = compOpt.get();
        try {
            var pending = company.getPendingAppointments().get(nomineeId);
            if (pending == null) return Result.failure("No pending nomination found.");
            CompanyRole assignedRole = pending.getProposedRole();
            String appointerId = pending.getAppointerId();

            company.acceptNomination(nomineeId);
            companyRepository.save(company);

            if (userOpt.get() instanceof Member m) {
                m.addCompanyRole(companyId, assignedRole, appointerId);
                userRepository.save(m);
            }
            logger.info("User {} accepted nomination for role {} in company {}.", nomineeId, assignedRole, companyId);
            return Result.success();
        } catch (Exception e) {
            return Result.failure(e.getMessage());
        }
    }

    public Result<Void> rejectNomination(String token, String companyId) {
        if (!authGateway.validateToken(token)) return Result.failure("User not authenticated.");
        String nomineeId = authGateway.extractUserId(token);

        Optional<ProductionCompany> compOpt = companyRepository.findById(companyId);
        if (compOpt.isEmpty()) return Result.failure("Company not found.");

        ProductionCompany company = compOpt.get();
        try {
            company.rejectNomination(nomineeId);
            companyRepository.save(company);
            logger.info("User {} rejected nomination in company {}.", nomineeId, companyId);
            return Result.success();
        } catch (Exception e) {
            return Result.failure(e.getMessage());
        }
    }

    public Result<List<OrderHistoryDTO>> viewCompanyOrders(String token, String companyId) {
        if (!authGateway.validateToken(token)) return Result.failure("User not authenticated.");
        String actingUserId = authGateway.extractUserId(token);
        Optional<ProductionCompany> compOpt = companyRepository.findById(companyId);
        if (compOpt.isEmpty()) return Result.failure("Company not found");
        ProductionCompany company = compOpt.get();
        if (!company.isOwner(actingUserId) && !company.hasPermission(actingUserId, CompanyPermission.VIEW_REPORTS)) {
            return Result.failure("Permission denied: VIEW_REPORTS required.");
        }
        List<OrderHistoryDTO> dtos = historyRepository.findByCompanyId(companyId).stream()
                .map(h -> objectMapper.convertValue(h, OrderHistoryDTO.class))
                .collect(Collectors.toList());
        return Result.success(dtos);
    }

    public Result<SalesReportDTO> generateSalesReport(String token, String companyId) {
        if (!authGateway.validateToken(token)) return Result.failure("User not authenticated.");
        String actingUserId = authGateway.extractUserId(token);
        Optional<ProductionCompany> compOpt = companyRepository.findById(companyId);
        if (compOpt.isEmpty()) return Result.failure("Company not found");
        ProductionCompany company = compOpt.get();
        if (!company.isOwner(actingUserId) && !company.hasPermission(actingUserId, CompanyPermission.VIEW_REPORTS)) {
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
        logger.info("Sales report generated for company {} by user {}.", companyId, actingUserId);
        return Result.success(new SalesReportDTO(companyId, company.getName(), orders.size(), totalRevenue, dtos));
    }

    public Result<Void> updatePermissions(String token, String companyId, String targetManagerUsername,
                                          Set<CompanyPermission> permissions) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized attempt to update permissions.");
            return Result.failure("User not authenticated.");
        }
        String actingUserId = authGateway.extractUserId(token);
        Optional<User> targetOpt = userRepository.findByUsername(targetManagerUsername);
        if (targetOpt.isEmpty()) return Result.failure("Target user not found or inactive.");

        String targetManagerId = targetOpt.get().getId();
        Optional<ProductionCompany> compOpt = companyRepository.findById(companyId);
        if (compOpt.isEmpty()) return Result.failure("Company not found");
        try {
            ProductionCompany company = compOpt.get();
            company.updatePermissions(actingUserId, targetManagerId, permissions);
            companyRepository.save(company);
            return Result.success();
        } catch (Exception e) {
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
}

