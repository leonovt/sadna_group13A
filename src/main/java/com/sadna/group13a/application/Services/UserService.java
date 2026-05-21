package com.sadna.group13a.application.Services;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sadna.group13a.domain.Interfaces.IOrderHistoryRepository;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import com.sadna.group13a.application.DTO.OrderHistoryDTO;
import com.sadna.group13a.application.DTO.UserDTO;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Interfaces.IPasswordEncoder;
import com.sadna.group13a.domain.Aggregates.OrderHistory.OrderHistory;
import com.sadna.group13a.domain.Aggregates.User.Guest;
import com.sadna.group13a.domain.Aggregates.User.Member;
import com.sadna.group13a.domain.Aggregates.User.User;
import org.springframework.stereotype.Service;

@Service
public class UserService
{
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final IUserRepository userRepository;
    private final IAuth authGateway;
    private final IPasswordEncoder passwordEncoder;
    private final IOrderHistoryRepository historyRepository;
    private final ObjectMapper objectMapper;

    public UserService(IUserRepository userRepository, IAuth authGateway, IPasswordEncoder passwordEncoder,
                       IOrderHistoryRepository historyRepository, ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.authGateway = authGateway;
        this.passwordEncoder = passwordEncoder;
        this.historyRepository = historyRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a Guest session: persists a Guest aggregate so the rest of the system
     * can look it up by id, then returns a signed token for that guest.
     */
    public Result<String> enterAsGuest() {
        String guestId = "guest-" + UUID.randomUUID();
        userRepository.save(new Guest(guestId, guestId));
        String token = authGateway.generateToken(guestId);
        logger.info("Guest session started: {}.", guestId);
        return Result.success(token);
    }

    /**
     * Registers a new customer in the system.
     */
    public Result<UserDTO> register(String username, String rawPassword) {
        logger.debug("Attempting to register new user: {}", username);

        try {
            if (username == null || username.isBlank()) {
                return Result.failure("Username cannot be empty.");
            }
            if (rawPassword == null || rawPassword.isBlank()) {
                return Result.failure("Password cannot be empty.");
            }

            if (userRepository.findByUsername(username).isPresent()) {
                logger.warn("Registration failed: Username '{}' is already taken.", username);
                return Result.failure("Username is already taken.");
            }

            String hashedPassword = passwordEncoder.encodePassword(rawPassword);
            
            User newUser = new Member(
                UUID.randomUUID().toString(),
                username,
                hashedPassword
            );

            userRepository.save(newUser);

            UserDTO dto = objectMapper.convertValue(newUser, UserDTO.class);
            logger.info("Successfully registered user: {}", username);
            
            return Result.success(dto);

        } catch (Exception e) {
            logger.error("An unexpected error occurred during registration for username: {}", username, e);
            return Result.failure("Registration failed due to an internal error.");
        }
    }

    /**
     * Authenticates a user and generates a JWT token for session management.
     */
    public Result<String> login(String username, String rawPassword) {
        logger.debug("Attempting login for user: {}", username);

        try {
            Optional<User> userOpt = userRepository.findByUsername(username);
            
            if (userOpt.isEmpty()) {
                logger.warn("Login failed: Username '{}' not found.", username);
                return Result.failure("Invalid username or password.");
            }

            User user = userOpt.get();

            if (!passwordEncoder.matches(rawPassword, user.getHashedPassword())) {
                logger.warn("Login failed: Incorrect password attempt for user '{}'.", username);
                return Result.failure("Invalid username or password.");
            }


            String jwtToken = authGateway.generateToken(user.getId());
            
            logger.info("Successfully authenticated user: {}", username);
            return Result.success(jwtToken);

        } catch (Exception e) {
            logger.error("An unexpected error occurred during login for username: {}", username, e);
            return Result.failure("Login failed due to an internal error.");
        }
    }

    /**
     * Retrieves the safe, public profile details of a user via their ID.
     */
    public Result<UserDTO> getUserProfile(String token)
    {
        if(!authGateway.validateToken(token)) 
        {
            logger.warn("Unauthorized profile access attempt with invalid token.");
            return Result.failure("Unauthorized.");
        }
        String userId = authGateway.extractUserId(token);
        logger.debug("Attempting to fetch profile for user ID: {}", userId);

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            logger.warn("Profile fetch failed: User ID {} not found.", userId);
            return Result.failure("User not found.");
        }

        User user = userOpt.get();
        UserDTO dto = objectMapper.convertValue(user, UserDTO.class);
        
        logger.info("Successfully retrieved profile for user: {}", user.getUsername());
        return Result.success(dto);
    }

    /**
     * Logs the current session out and returns a fresh guest token,
     * transitioning the caller back to guest mode.
     */
    public Result<String> logout(String token) {
        if (!authGateway.validateToken(token)) return Result.failure("Unauthorized.");
        logger.info("User {} logged out.", authGateway.extractUserId(token));
        return enterAsGuest();
    }

    public Result<UserDTO> updateProfile(String token, String newUsername) {
        if (!authGateway.validateToken(token)) return Result.failure("Unauthorized.");
        if (newUsername == null || newUsername.isBlank()) return Result.failure("Username cannot be blank.");

        String userId = authGateway.extractUserId(token);
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) return Result.failure("User not found.");

        if (userRepository.findByUsername(newUsername).isPresent()) return Result.failure("Username already taken.");

        User user = userOpt.get();
        user.setUsername(newUsername);
        userRepository.save(user);

        logger.info("User {} changed username to '{}'.", userId, newUsername);
        return Result.success(objectMapper.convertValue(user, UserDTO.class));
    }

    // ── Order History ─────────────────────────────────────────────

    public Result<List<OrderHistoryDTO>> viewOrderHistory(String token)
    {
        if(!authGateway.validateToken(token))
        {
            logger.warn("Unauthorized order history access attempt with invalid token.");
            return Result.failure("Unauthorized.");
        }
        String userId = authGateway.extractUserId(token);
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty() || !userOpt.get().canPurchase()) {
            return Result.failure("Only registered members can view order history.");
        }
        
        List<OrderHistory> histories = historyRepository.findByUserId(userId);

        List<OrderHistoryDTO> dtos = histories.stream()
            .map(history -> objectMapper.convertValue(history, OrderHistoryDTO.class))
            .collect(Collectors.toList());

        return Result.success(dtos);
    }
}
