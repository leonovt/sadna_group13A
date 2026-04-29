package com.sadna.group13a.application.service;

import com.sadna.group13a.application.dto.Result;
import com.sadna.group13a.domain.external.IAuthGateway;
import com.sadna.group13a.domain.user.Admin;
import com.sadna.group13a.domain.user.IUserRepository;

import java.util.UUID;

/**
 * Application service for system-level operations (e.g. initialization).
 * Implements UC 1.1 (Initialize Platform).
 */
public class SystemService {
    private final IUserRepository userRepository;
    private final IAuthGateway authGateway;
    private boolean isInitialized = false;

    public SystemService(IUserRepository userRepository, IAuthGateway authGateway) {
        this.userRepository = userRepository;
        this.authGateway = authGateway;
    }

    /**
     * Initializes the platform and creates the root system administrator.
     */
    public Result<Void> initializePlatform(String adminUsername, String rawPassword) {
        if (isInitialized) {
            return Result.failure("System is already initialized.");
        }
        try {
            if (userRepository.findByUsername(adminUsername).isPresent()) {
                return Result.failure("Admin username is already taken.");
            }
            
            String hashedPassword = authGateway.hashPassword(rawPassword);
            Admin rootAdmin = new Admin(UUID.randomUUID().toString(), adminUsername, hashedPassword);
            
            userRepository.save(rootAdmin);
            this.isInitialized = true;
            return Result.success();
        } catch (Exception e) {
            return Result.failure("Failed to initialize platform: " + e.getMessage());
        }
    }

    public boolean isPlatformInitialized() {
        return isInitialized;
    }
}
