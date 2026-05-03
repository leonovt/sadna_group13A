package com.sadna.group13a.application.Services;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Interfaces.IPaymentGateway;
import com.sadna.group13a.application.Interfaces.ITicketSupplier;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import com.sadna.group13a.application.Interfaces.IPasswordEncoder;
import com.sadna.group13a.domain.Aggregates.User.Admin;
import com.sadna.group13a.application.Result;
import org.springframework.stereotype.Service;

@Service
public class SystemService
{
    private static final Logger logger = LoggerFactory.getLogger(SystemService.class);
    private final IUserRepository userRepository;
    private final IAuth authGateway;
    private final IPaymentGateway paymentGateway;
    private final ITicketSupplier ticketingGateway;
    private final IPasswordEncoder passwordEncoder;

    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    public SystemService(IUserRepository userRepository, 
                         IAuth authGateway,
                         IPaymentGateway paymentGateway,
                         ITicketSupplier ticketingGateway,
                         IPasswordEncoder passwordEncoder) 
    {
        this.userRepository = userRepository;
        this.authGateway = authGateway;
        this.paymentGateway = paymentGateway;
        this.ticketingGateway = ticketingGateway;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Initializes the platform, verifies external services, and creates the root system administrator.
     */
    public Result<Void> initializePlatform(String adminUsername, String rawPassword)
    {
        logger.info("Attempting to initialize the platform...");
        // compareAndSet atomically checks if it's false, and if so, sets it to true.
        // If it was already true, it returns false, meaning we shouldn't proceed.
        if (!isInitialized.compareAndSet(false, true))
        {
            logger.warn("Platform initialization aborted: System is already initialized.");
            return Result.failure("System is already initialized or initialization is in progress.");
        }

        try {
            // 1. Verify connections to external services as required by the spec
            if (!paymentGateway.isConnected()) {
                isInitialized.set(false); // Rollback state
                logger.error("Platform initialization failed: Payment gateway is unreachable.");
                return Result.failure("Failed to connect to the external payment service.");
            }
            if (!ticketingGateway.isConnected()) {
                isInitialized.set(false); // Rollback state
                logger.error("Platform initialization failed: Ticketing gateway is unreachable.");
                return Result.failure("Failed to connect to the external ticketing service.");
            }

            // 2. Create the Root Admin
            if (userRepository.findByUsername(adminUsername).isPresent()) {
                isInitialized.set(false); // Rollback state
                logger.warn("Platform initialization aborted: Admin username '{}' is already taken.", adminUsername);
                return Result.failure("Admin username is already taken.");
            }
            
            String hashedPassword = passwordEncoder.encodePassword(rawPassword);
            Admin rootAdmin = new Admin(UUID.randomUUID().toString(), adminUsername, hashedPassword);
            
            userRepository.save(rootAdmin);
            logger.info("Platform initialized successfully. Root admin '{}' created.", adminUsername);
            return Result.success();

        } catch (Exception e) {
            isInitialized.set(false); // Rollback state on unexpected failure
            logger.error("Platform initialization encountered an unexpected error: {}", e.getMessage(), e);
            return Result.failure("Failed to initialize platform: " + e.getMessage());
        }
    }

    public boolean isPlatformInitialized() {
        return isInitialized.get();
    }
}