package com.sadna.group13a.application.Services;

import org.slf4j.LoggerFactory;

import java.util.Optional;

import org.slf4j.Logger;

import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.domain.Interfaces.ICompanyRepository;
import com.sadna.group13a.domain.Interfaces.IEventRepository;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import com.sadna.group13a.domain.Aggregates.User.User;
import com.sadna.group13a.domain.Aggregates.Admin.Admin;
import com.sadna.group13a.domain.Aggregates.Event.Event;
import com.sadna.group13a.domain.Aggregates.Company.Company;

import com.sadna.group13a.application.Result;

public class AdminService
{
    private static final Logger logger = LoggerFactory.getLogger(SystemService.class);
    private final IUserRepository userRepository;
    private final IEventRepository eventRepository;
    private final ICompanyRepository companyRepository;
    private final IAuth authGateway;

    public AdminService(IUserRepository userRepository, IEventRepository eventRepository, ICompanyRepository companyRepository, IAuth authGateway)
    {
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
        this.companyRepository = companyRepository;
        this.authGateway = authGateway;
    }

    /**
     * Deactivates a user account, preventing them from logging in or purchasing.
     */
    public Result<Void> deactivateUser(String token, String targetUsername)
    {
        if(!authGateway.validateToken(token)) {
            logger.warn("Unauthorized attempt to deactivate user with token: {}", token);
            return Result.failure("Unauthorized: Invalid token.");
        }
        String adminId = authGateway.extractUserId(token);
        Optional<User> targetUserOpt = userRepository.findByUsername(targetUsername);
        if (targetUserOpt.isEmpty()) {
            return Result.failure("Target user not found.");
        }
        String targetUserId = targetUserOpt.get().getId();
        Optional<User> adminOpt = userRepository.findById(adminId);
        if (adminOpt.isEmpty() || !(adminOpt.get() instanceof Admin)) {
            return Result.failure("Unauthorized: Only Admins can deactivate users.");
        }
        
        Optional<User> targetOpt = userRepository.findById(targetUserId);
        if (targetOpt.isEmpty()) {
            return Result.failure("Target user not found.");
        }
        
        User target = targetOpt.get();
        if (target instanceof Admin) {
            return Result.failure("Cannot deactivate another admin.");
        }

        try {
            Admin admin = (Admin) adminOpt.get();
            admin.deactivateUser(target); // applies business rule in domain
            userRepository.save(target);
            return Result.success();
        } catch (Exception e) {
            return Result.failure("Failed to deactivate user: " + e.getMessage());
        }
    }

    public Result<Void> reactivateUser(String token, String targetUsername)
    {
        if(!authGateway.validateToken(token)) {
            logger.warn("Unauthorized attempt to reactivate user with token: {}", token);
            return Result.failure("Unauthorized: Invalid token.");
        }
        String adminId = authGateway.extractUserId(token);
        Optional<User> targetUserOpt = userRepository.findByUsername(targetUsername);
        if (targetUserOpt.isEmpty()) {
            return Result.failure("Target user not found.");
        }
        String targetUserId = targetUserOpt.get().getId();
        Optional<User> adminOpt = userRepository.findById(adminId);
        if (adminOpt.isEmpty() || !(adminOpt.get() instanceof Admin)) {
            return Result.failure("Unauthorized: Only Admins can reactivate users.");
        }
        
        Optional<User> targetOpt = userRepository.findById(targetUserId);
        if (targetOpt.isEmpty()) return Result.failure("Target user not found.");
        
        User target = targetOpt.get();
        Admin admin = (Admin) adminOpt.get();
        admin.activateUser(target);
        userRepository.save(target);
        return Result.success();
    }

    public Result<Void> cancelEventGlobally(String token, String eventId)
    {
        if(!authGateway.validateToken(token)) {
            logger.warn("Unauthorized attempt to cancel event with token: {}", token);
            return Result.failure("Unauthorized: Invalid token.");
        }
        String adminId = authGateway.extractUserId(token);
        Optional<User> adminOpt = userRepository.findById(adminId);
        if (adminOpt.isEmpty() || !(adminOpt.get() instanceof Admin)) {
            return Result.failure("Unauthorized: Only Admins can cancel events.");
        }
        
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) return Result.failure("Event not found.");
        
        Event event = eventOpt.get();
        event.unpublish();
        // Assume we might also refund users in a complete implementation
        eventRepository.save(event);
        return Result.success();
    }
    
    public Result<Void> closeCompanyGlobally(String token, String companyId)
    {
        if(!authGateway.validateToken(token)) {
            logger.warn("Unauthorized attempt to close company with token: {}", token);
            return Result.failure("Unauthorized: Invalid token.");
        }
        String adminId = authGateway.extractUserId(token);
        Optional<User> adminOpt = userRepository.findById(adminId);
        if (adminOpt.isEmpty() || !(adminOpt.get() instanceof Admin)) {
            return Result.failure("Unauthorized: Only Admins can close companies.");
        }
        
        Optional<Company> compOpt = companyRepository.findById(companyId);
        if (compOpt.isEmpty()) return Result.failure("Company not found.");
        
        Company company = compOpt.get();
        // Since Admin is not a founder, we just force close it
        // Or we use a specific admin override method on company if one exists.
        // For V1, we might just need to add a force close method to company, or bypass domain safely.
        // I will just use reflection or assume a suspend method exists for admin override.
        return Result.failure("Global close company not fully supported by domain rules yet.");
    }
}
