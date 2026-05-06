package com.sadna.group13a.application.Services;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Interfaces.IPaymentGateway;
import com.sadna.group13a.application.Interfaces.ITicketSupplier;
import com.sadna.group13a.domain.Interfaces.IAdminRepository;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import com.sadna.group13a.application.Interfaces.IPasswordEncoder;
import com.sadna.group13a.domain.Aggregates.Admin.Admin;
import com.sadna.group13a.domain.Aggregates.User.Member;
import com.sadna.group13a.application.Result;
import org.springframework.stereotype.Service;

@Service
public class SystemService {

    private static final Logger logger = LoggerFactory.getLogger(SystemService.class);

    private final IUserRepository userRepository;
    private final IAdminRepository adminRepository;
    private final IAuth authGateway;
    private final IPaymentGateway paymentGateway;
    private final ITicketSupplier ticketingGateway;
    private final IPasswordEncoder passwordEncoder;

    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    public SystemService(IUserRepository userRepository,
                         IAdminRepository adminRepository,
                         IAuth authGateway,
                         IPaymentGateway paymentGateway,
                         ITicketSupplier ticketingGateway,
                         IPasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.adminRepository = adminRepository;
        this.authGateway = authGateway;
        this.paymentGateway = paymentGateway;
        this.ticketingGateway = ticketingGateway;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Initializes the platform, verifies external services, and creates the root system administrator.
     * The root admin is stored as a Member (for auth) and as an Admin record (for governance).
     */
    public Result<Void> initializePlatform(String adminUsername, String rawPassword) {
        logger.info("Attempting to initialize the platform...");
        if (!isInitialized.compareAndSet(false, true)) {
            logger.warn("Platform initialization aborted: System is already initialized.");
            return Result.failure("System is already initialized or initialization is in progress.");
        }

        try {
            if (!paymentGateway.isConnected()) {
                isInitialized.set(false);
                logger.error("Platform initialization failed: Payment gateway is unreachable.");
                return Result.failure("Failed to connect to the external payment service.");
            }
            if (!ticketingGateway.isConnected()) {
                isInitialized.set(false);
                logger.error("Platform initialization failed: Ticketing gateway is unreachable.");
                return Result.failure("Failed to connect to the external ticketing service.");
            }

            if (userRepository.findByUsername(adminUsername).isPresent()) {
                isInitialized.set(false);
                logger.warn("Platform initialization aborted: Admin username '{}' is already taken.", adminUsername);
                return Result.failure("Admin username is already taken.");
            }

            // Create a Member for auth (login/password) + an Admin record for governance
            String memberId = UUID.randomUUID().toString();
            String hashedPassword = passwordEncoder.encodePassword(rawPassword);
            Member rootMember = new Member(memberId, adminUsername, hashedPassword);
            userRepository.save(rootMember);

            Admin rootAdmin = new Admin(UUID.randomUUID().toString(), memberId);
            adminRepository.save(rootAdmin);

            logger.info("Platform initialized successfully. Root admin '{}' created.", adminUsername);
            return Result.success();

        } catch (Exception e) {
            isInitialized.set(false);
            logger.error("Platform initialization encountered an unexpected error: {}", e.getMessage(), e);
            return Result.failure("Failed to initialize platform: " + e.getMessage());
        }
    }

    public boolean isPlatformInitialized() {
        return isInitialized.get();
    }
}
