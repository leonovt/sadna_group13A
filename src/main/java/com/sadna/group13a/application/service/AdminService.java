package com.sadna.group13a.application.service;

import com.sadna.group13a.application.dto.Result;
import com.sadna.group13a.domain.user.IUserRepository;
import com.sadna.group13a.domain.user.User;

import java.util.Optional;

/**
 * Application service for Global System Administration.
 * Implements UC 5.1 (Admin Dashboard/Manage System).
 */
public class AdminService {
    private final IUserRepository userRepository;
    private final com.sadna.group13a.domain.event.IEventRepository eventRepository;
    private final com.sadna.group13a.domain.company.ICompanyRepository companyRepository;

    public AdminService(IUserRepository userRepository, com.sadna.group13a.domain.event.IEventRepository eventRepository, com.sadna.group13a.domain.company.ICompanyRepository companyRepository) {
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
        this.companyRepository = companyRepository;
    }

    /**
     * Deactivates a user account, preventing them from logging in or purchasing.
     */
    public Result<Void> deactivateUser(String adminId, String targetUserId) {
        Optional<User> adminOpt = userRepository.findById(adminId);
        if (adminOpt.isEmpty() || !(adminOpt.get() instanceof com.sadna.group13a.domain.user.Admin)) {
            return Result.failure("Unauthorized: Only Admins can deactivate users.");
        }
        
        Optional<User> targetOpt = userRepository.findById(targetUserId);
        if (targetOpt.isEmpty()) {
            return Result.failure("Target user not found.");
        }
        
        User target = targetOpt.get();
        if (target instanceof com.sadna.group13a.domain.user.Admin) {
            return Result.failure("Cannot deactivate another admin.");
        }

        try {
            com.sadna.group13a.domain.user.Admin admin = (com.sadna.group13a.domain.user.Admin) adminOpt.get();
            admin.deactivateUser(target); // applies business rule in domain
            userRepository.save(target);
            return Result.success();
        } catch (Exception e) {
            return Result.failure("Failed to deactivate user: " + e.getMessage());
        }
    }

    public Result<Void> reactivateUser(String adminId, String targetUserId) {
        Optional<User> adminOpt = userRepository.findById(adminId);
        if (adminOpt.isEmpty() || !(adminOpt.get() instanceof com.sadna.group13a.domain.user.Admin)) {
            return Result.failure("Unauthorized: Only Admins can reactivate users.");
        }
        
        Optional<User> targetOpt = userRepository.findById(targetUserId);
        if (targetOpt.isEmpty()) return Result.failure("Target user not found.");
        
        User target = targetOpt.get();
        com.sadna.group13a.domain.user.Admin admin = (com.sadna.group13a.domain.user.Admin) adminOpt.get();
        admin.activateUser(target);
        userRepository.save(target);
        return Result.success();
    }

    public Result<Void> cancelEventGlobally(String adminId, String eventId) {
        Optional<User> adminOpt = userRepository.findById(adminId);
        if (adminOpt.isEmpty() || !(adminOpt.get() instanceof com.sadna.group13a.domain.user.Admin)) {
            return Result.failure("Unauthorized: Only Admins can cancel events.");
        }
        
        Optional<com.sadna.group13a.domain.event.Event> eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) return Result.failure("Event not found.");
        
        com.sadna.group13a.domain.event.Event event = eventOpt.get();
        event.unpublish();
        // Assume we might also refund users in a complete implementation
        eventRepository.save(event);
        return Result.success();
    }

    public Result<Void> closeCompanyGlobally(String adminId, String companyId) {
        Optional<User> adminOpt = userRepository.findById(adminId);
        if (adminOpt.isEmpty() || !(adminOpt.get() instanceof com.sadna.group13a.domain.user.Admin)) {
            return Result.failure("Unauthorized: Only Admins can close companies.");
        }
        
        Optional<com.sadna.group13a.domain.company.ProductionCompany> compOpt = companyRepository.findById(companyId);
        if (compOpt.isEmpty()) return Result.failure("Company not found.");
        
        com.sadna.group13a.domain.company.ProductionCompany company = compOpt.get();
        // Since Admin is not a founder, we just force close it
        // Or we use a specific admin override method on company if one exists.
        // For V1, we might just need to add a force close method to company, or bypass domain safely.
        // I will just use reflection or assume a suspend method exists for admin override.
        return Result.failure("Global close company not fully supported by domain rules yet.");
    }
}
