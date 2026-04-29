package com.sadna.group13a.application.service;

import com.sadna.group13a.application.dto.CompanyDTO;
import com.sadna.group13a.application.dto.Result;
import com.sadna.group13a.application.dto.StaffMemberDTO;
import com.sadna.group13a.domain.company.CompanyRole;
import com.sadna.group13a.domain.company.ICompanyRepository;
import com.sadna.group13a.domain.company.ProductionCompany;
import com.sadna.group13a.domain.user.IUserRepository;
import com.sadna.group13a.domain.user.User;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Application service for managing Production Companies.
 * Implements UC 4.1 (Open Company), UC 4.5 (Appoint Manager), etc.
 */
public class CompanyService {
    private final ICompanyRepository companyRepository;
    private final IUserRepository userRepository;

    public CompanyService(ICompanyRepository companyRepository, IUserRepository userRepository) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
    }

    /**
     * Opens a new Production Company.
     */
    public Result<String> createCompany(String founderId, String name, String description) {
        Optional<User> founderOpt = userRepository.findById(founderId);
        if (founderOpt.isEmpty() || !founderOpt.get().isActive()) {
            return Result.failure("Founder not found or inactive.");
        }
        
        ProductionCompany company = new ProductionCompany(UUID.randomUUID().toString(), name, founderId);
        companyRepository.save(company);
        return Result.success(company.getId());
    }

    /**
     * Appoints a new manager to the company.
     */
    public Result<Void> appointManager(String companyId, String initiatorId, String targetUserId) {
        Optional<ProductionCompany> compOpt = companyRepository.findById(companyId);
        if (compOpt.isEmpty()) return Result.failure("Company not found");
        
        Optional<User> targetOpt = userRepository.findById(targetUserId);
        if (targetOpt.isEmpty() || !targetOpt.get().isActive()) return Result.failure("Target user not found or inactive.");
        
        ProductionCompany company = compOpt.get();
        try {
            company.nominateStaff(initiatorId, targetUserId, CompanyRole.MANAGER, null);
            company.acceptNomination(targetUserId); // Auto-accept for simplicity in this service
            companyRepository.save(company);
            return Result.success();
        } catch (Exception e) {
            return Result.failure(e.getMessage());
        }
    }

    /**
     * Retrieves a company's details.
     */
    public Result<CompanyDTO> getCompany(String companyId) {
        Optional<ProductionCompany> compOpt = companyRepository.findById(companyId);
        if (compOpt.isEmpty()) return Result.failure("Company not found");
        
        ProductionCompany company = compOpt.get();
        var staffDTOs = company.getStaff().values().stream()
            .map(s -> new StaffMemberDTO(s.getUserId(), s.getRole(), s.getPermissions()))
            .collect(Collectors.toList());
            
        CompanyDTO dto = new CompanyDTO(
            company.getId(),
            company.getName(),
            "", // no description
            company.getStatus(),
            company.getStaff().values().stream().filter(s -> s.getRole() == com.sadna.group13a.domain.company.CompanyRole.FOUNDER).findFirst().map(s -> s.getUserId()).orElse(""),
            staffDTOs
        );
        return Result.success(dto);
    }

    public Result<Void> suspendCompany(String actingUserId, String companyId) {
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

    public Result<Void> reopenCompany(String actingUserId, String companyId) {
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

    public Result<java.util.List<StaffMemberDTO>> getRoleTree(String actingUserId, String companyId) {
        Optional<ProductionCompany> compOpt = companyRepository.findById(companyId);
        if (compOpt.isEmpty()) return Result.failure("Company not found");
        try {
            var treeMap = compOpt.get().getRoleTree(actingUserId);
            var dtos = treeMap.values().stream()
                .map(s -> new StaffMemberDTO(s.getUserId(), s.getRole(), s.getPermissions()))
                .collect(Collectors.toList());
            return Result.success(dtos);
        } catch (Exception e) {
            return Result.failure(e.getMessage());
        }
    }

    public Result<Void> fireManager(String actingUserId, String companyId, String targetUserId) {
        Optional<ProductionCompany> compOpt = companyRepository.findById(companyId);
        if (compOpt.isEmpty()) return Result.failure("Company not found");
        try {
            ProductionCompany company = compOpt.get();
            company.fireStaff(actingUserId, targetUserId);
            companyRepository.save(company);
            return Result.success();
        } catch (Exception e) {
            return Result.failure(e.getMessage());
        }
    }

    public Result<Void> resign(String actingUserId, String companyId) {
        Optional<ProductionCompany> compOpt = companyRepository.findById(companyId);
        if (compOpt.isEmpty()) return Result.failure("Company not found");
        try {
            ProductionCompany company = compOpt.get();
            company.resign(actingUserId);
            companyRepository.save(company);
            return Result.success();
        } catch (Exception e) {
            return Result.failure(e.getMessage());
        }
    }

    public Result<Void> updatePermissions(String actingUserId, String companyId, String targetManagerId, java.util.Set<com.sadna.group13a.domain.company.CompanyPermission> permissions) {
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
}
