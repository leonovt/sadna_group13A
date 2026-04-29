package com.sadna.group13a.application.service;

import com.sadna.group13a.application.dto.Result;
import com.sadna.group13a.domain.external.IAuthGateway;
import com.sadna.group13a.domain.user.Guest;
import com.sadna.group13a.domain.user.IUserRepository;
import com.sadna.group13a.domain.user.Member;
import com.sadna.group13a.domain.user.SessionToken;
import com.sadna.group13a.domain.user.User;

import java.util.Optional;
import java.util.UUID;

/**
 * Application service for user authentication, registration, and guest sessions.
 * Implements UC 1.3 (Generate Guest), UC 1.4 (Register), UC 2.x (Login/Logout).
 */
public class AuthService {
    private final IUserRepository userRepository;
    private final IAuthGateway authGateway;

    public AuthService(IUserRepository userRepository, IAuthGateway authGateway) {
        this.userRepository = userRepository;
        this.authGateway = authGateway;
    }

    /**
     * Generates a new Guest session for a visiting user.
     */
    public Result<String> enterAsGuest() {
        Guest guest = new Guest(UUID.randomUUID().toString());
        userRepository.save(guest);
        
        // System generates a basic token for tracking the guest's cart
        String token = authGateway.generateToken(guest.getId(), guest.getRole().name());
        return Result.success(token);
    }

    /**
     * Registers a new Member in the system.
     */
    public Result<Void> register(String username, String rawPassword) {
        if (username == null || username.isBlank() || rawPassword == null || rawPassword.isBlank()) {
            return Result.failure("Username and password are required");
        }
        
        if (userRepository.findByUsername(username).isPresent()) {
            return Result.failure("Username already exists");
        }

        String hashedPassword = authGateway.hashPassword(rawPassword);
        Member member = new Member(UUID.randomUUID().toString(), username, hashedPassword);
        
        userRepository.save(member);
        return Result.success();
    }

    /**
     * Authenticates a Member/Admin and returns a session token.
     */
    public Result<String> login(String username, String rawPassword) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        
        if (userOpt.isEmpty() || !(userOpt.get() instanceof Member)) {
            // Note: Admin also extends User, but Admin might have a different logic or we can treat Admin as Member
            // In our domain, Admin inherits from User directly, not Member. Let's fix this check.
            if (userOpt.isEmpty()) {
                return Result.failure("Invalid credentials");
            }
        }
        
        User user = userOpt.get();
        String storedHash;
        
        if (user instanceof Member m) {
            storedHash = m.getPasswordHash();
        } else if (user instanceof com.sadna.group13a.domain.user.Admin a) {
            storedHash = a.getPasswordHash();
        } else {
            return Result.failure("User type cannot login");
        }

        if (!authGateway.verifyPassword(rawPassword, storedHash)) {
            return Result.failure("Invalid credentials");
        }
        
        String tokenString = authGateway.generateToken(user.getId(), user.getRole().name());
        SessionToken sessionToken = new SessionToken(tokenString, java.time.Instant.now(), java.time.Instant.now().plusSeconds(3600));
        
        if (user instanceof Member m) {
            try {
                m.login(sessionToken);
            } catch (Exception e) {
                return Result.failure(e.getMessage());
            }
        } else if (user instanceof com.sadna.group13a.domain.user.Admin a) {
            // Admin doesn't have login() method with SessionToken in current model, 
            // but we can just return the token if they authenticate correctly.
        }

        userRepository.save(user);
        return Result.success(tokenString);
    }

    /**
     * Logs out the current user by destroying their session token.
     */
    public Result<Void> logout(String userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent() && userOpt.get() instanceof Member m) {
            m.logout();
            userRepository.save(m);
        }
        return Result.success();
    }
}
