package com.sadna.group13a.application.Services;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.domain.Interfaces.ICompanyRepository;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.domain.Aggregates.User.User;
import com.sadna.group13a.domain.Aggregates.Company.Company;
import com.sadna.group13a.domain.shared.CompanyRole;

public class CompanyService
{
    private static final Logger logger = LoggerFactory.getLogger(SystemService.class);
    private final ICompanyRepository companyRepository;
    private final IUserRepository userRepository;
    private final IAuth authGateway;

    public CompanyService(ICompanyRepository companyRepository, IUserRepository userRepository, IAuth authGateway)
    {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.authGateway = authGateway;
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
        return Result.success(true);
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

}
