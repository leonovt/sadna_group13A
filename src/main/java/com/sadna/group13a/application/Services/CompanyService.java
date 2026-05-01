package com.sadna.group13a.application.Services;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Set;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.domain.Interfaces.ICompanyRepository;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.domain.Aggregates.User.User;
import com.sadna.group13a.domain.Aggregates.Company.Company;
import com.sadna.group13a.domain.shared.CompanyRole;
import com.sadna.group13a.application.DTO.CompanyDTO;
import com.sadna.group13a.application.DTO.StaffMemberDTO;
import com.sadna.group13a.domain.shared.CompanyPermission;

public class CompanyService
{
    private static final Logger logger = LoggerFactory.getLogger(SystemService.class);
    private final ObjectMapper objectMapper;
    private final ICompanyRepository companyRepository;
    private final IUserRepository userRepository;
    private final IAuth authGateway;

    public CompanyService(ICompanyRepository companyRepository, IUserRepository userRepository, IAuth authGateway, ObjectMapper objectMapper)
    {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.authGateway = authGateway;
        this.objectMapper = objectMapper;
    }

    /**
     * Opens a new Production Company.
     */
    public Result<Boolean> createCompany(String token, String name, String description)
    {
        if(!authGateway.validateToken(token)) 
        {
            return Result.failure("User not authenticated.");
            logger.warn("Unauthorized attempt to create a company with token: {}", token);
        }
        String founderId = authGateway.extractUserId(token);
        Optional<User> founderOpt = userRepository.findById(founderId);
        if (founderOpt.isEmpty()) 
        {
            return Result.failure("Founder not found or inactive.");
        }
        
        Company company = new Company(UUID.randomUUID().toString(), name, founderId);
        companyRepository.save(company);
        return Result.success();
    }

    /**
     * Appoints a new manager to the company.
     */
    public Result<Void> appointManager(String token,String companyId, String targetUsername)
    {
        if(!authGateway.validateToken(token)) 
        {
            return Result.failure("User not authenticated.");
            logger.warn("Unauthorized attempt to appoint a manager with token: {}", token);
        }
        
        Optional<Company> compOpt = companyRepository.findById(companyId);
        if (compOpt.isEmpty()) return Result.failure("Company not found");
        
        Optional<User> targetOpt = userRepository.findByUsername(targetUsername);
        if (targetOpt.isEmpty())
        {
            return Result.failure("Target user not found or inactive.");
        }
        String targetUserId = targetOpt.get().getId();
        String initiatorId = authGateway.extractUserId(token);
        Company company = compOpt.get();
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
    public Result<CompanyDTO> getCompany(String token, String companyId) 
    {
        if(!authGateway.validateToken(token)) 
        {
            return Result.failure("User not authenticated.");
            logger.warn("Unauthorized attempt to get company details with token: {}", token);
        }

        Optional<Company> compOpt = companyRepository.findById(companyId);
        if (compOpt.isEmpty()) return Result.failure("Company not found");
        
        Company company = compOpt.get();

        // This is perfectly clean application logic!
        var staffDTOs = company.getStaff().values().stream()
            .map(s -> new StaffMemberDTO(s.getUserId(), s.getRole(), s.getPermissions()))
            .toList(); // slightly cleaner modern Java syntax
            
        String founderId = company.getStaff().values().stream()
            .filter(s -> s.getRole() == CompanyRole.FOUNDER)
            .findFirst()
            .map(StaffMember::getUserId) // method reference makes it cleaner
            .orElse("");

        CompanyDTO dto = new CompanyDTO(
            company.getId(),
            company.getName(),
            company.getDescription(),
            company.getStatus(),
            founderId,
            staffDTOs
        );
        return Result.success(dto);
    }

    public Result<Void> suspendCompany(String token, String companyId)
    {
        if(!authGateway.validateToken(token)) 
        {
            return Result.failure("User not authenticated.");
            logger.warn("Unauthorized attempt to suspend a company with token: {}", token);
        }
        String actingUserId = authGateway.extractUserId(token);
        Optional<Company> compOpt = companyRepository.findById(companyId);
        if (compOpt.isEmpty()) return Result.failure("Company not found");
        try {
            Company company = compOpt.get();
            company.suspendCompany(actingUserId);
            companyRepository.save(company);
            return Result.success();
        } catch (Exception e) {
            return Result.failure(e.getMessage());
        }
    }

    public Result<Void> reopenCompany(String token, String companyId)
    {
        if(!authGateway.validateToken(token)) 
        {
            return Result.failure("User not authenticated.");
            logger.warn("Unauthorized attempt to reopen a company with token: {}", token);
        }
        String actingUserId = authGateway.extractUserId(token);
        Optional<Company> compOpt = companyRepository.findById(companyId);
        if (compOpt.isEmpty()) return Result.failure("Company not found");
        try {
            Company company = compOpt.get();
            company.reopenCompany(actingUserId);
            companyRepository.save(company);
            return Result.success();
        } catch (Exception e) {
            return Result.failure(e.getMessage());
        }
    }

    public Result<List<StaffMemberDTO>> getRoleTree(String token, String companyId)
    {
        
        if(!authGateway.validateToken(token)) {
            logger.warn("Unauthorized attempt to get role tree with token: {}", token);
            return Result.failure("User not authenticated.");
        }
        
        String actingUserId = authGateway.extractUserId(token);
        
        Optional<Company> compOpt = companyRepository.findById(companyId);
        if (compOpt.isEmpty()) {
            return Result.failure("Company not found");
        }
        
        try {
            var treeMap = compOpt.get().getRoleTree(actingUserId);
            
            var dtos = treeMap.values().stream()
                .map(s -> new StaffMemberDTO(s.getUserId(), s.getRole(), s.getPermissions()))
                .collect(Collectors.toList());
                
            return Result.success(dtos);
            
        } catch (Exception e) {
            // FIX 2: You must log the exception here! 
            logger.error("Error retrieving role tree for company {} by user {}", companyId, actingUserId, e);
            return Result.failure(e.getMessage());
        }
    }

    public Result<Void> fireManager(String token, String companyId, String targetUsername)
    {
        if(!authGateway.validateToken(token)) 
        {
            return Result.failure("User not authenticated.");
            logger.warn("Unauthorized attempt to fire a manager with token: {}", token);
        }

        String actingUserId = authGateway.extractUserId(token);
        
        Optional<User> targetOpt = userRepository.findByUsername(targetUsername);
        if (targetOpt.isEmpty())
        {
            logger.warn("Attempt to fire manager failed: Target user '{}' not found.", targetUsername);
            return Result.failure("Target user not found or inactive.");
        
        
        }

        String targetUserId = targetOpt.get().getUserId();
        Optional<Company> compOpt = companyRepository.findById(companyId);
        
        if (compOpt.isEmpty()) return Result.failure("Company not found");
        try {
            Company company = compOpt.get();
            company.fireStaff(actingUserId, targetUserId);
            companyRepository.save(company);
            return Result.success();
        } catch (Exception e) {
            return Result.failure(e.getMessage());
        }
    }

    public Result<Void> resign(String token, String companyId)
    {
        if(!authGateway.validateToken(token)) 
        {
            return Result.failure("User not authenticated.");
            logger.warn("Unauthorized attempt to resign from a company with token: {}", token);
        }
        String actingUserId = authGateway.extractUserId(token);
        Optional<Company> compOpt = companyRepository.findById(companyId);
        if (compOpt.isEmpty()) return Result.failure("Company not found");
        try {
            Company company = compOpt.get();
            company.resign(actingUserId);
            companyRepository.save(company);
            return Result.success();
        } catch (Exception e) {
            return Result.failure(e.getMessage());
        }
    }

    public Result<Void> updatePermissions(String token, String companyId, String targetManagerUsername, Set<CompanyPermission> permissions)
    {
        if(!authGateway.validateToken(token)) 
        {
            return Result.failure("User not authenticated.");
            logger.warn("Unauthorized attempt to update permissions with token: {}", token);
        }

        String actingUserId = authGateway.extractUserId(token);
        Optional<User> targetOpt = userRepository.findByUsername(targetManagerUsername);
        if (targetOpt.isEmpty())
        {
            logger.warn("Attempt to update permissions failed: Target user '{}' not found.", targetManagerUsername);
            return Result.failure("Target user not found or inactive.");
        }
        String targetManagerId = targetOpt.get().getUserId();
        Optional<Company> compOpt = companyRepository.findById(companyId);
        if (compOpt.isEmpty()) return Result.failure("Company not found");
        try {
            Company company = compOpt.get();
            company.updatePermissions(actingUserId, targetManagerId, permissions);
            companyRepository.save(company);
            return Result.success();
        } catch (Exception e) {
            return Result.failure(e.getMessage());
        }
    }
}
