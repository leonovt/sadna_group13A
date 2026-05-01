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
import com.sadna.group13a.domain.shared.UserRole;
import com.sadna.group13a.domain.shared.UserState;
import com.sadna.group13a.application.DTO.OrderHistoryDTO;
import com.sadna.group13a.application.DTO.OrderHistoryItemDTO;
import com.sadna.group13a.application.DTO.UserDTO;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Interfaces.IPasswordEncoder;
import com.sadna.group13a.domain.Aggregates.OrderHistory.OrderHistory;
import com.sadna.group13a.domain.Aggregates.User.User;

public class UserService
{
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final IUserRepository userRepository;
    private final IAuth authGateway;
    private final IPasswordEncoder passwordEncoder;
    private final IOrderHistoryRepository historyRepository;

    private final ObjectMapper objectMapper;

    public UserService(IUserRepository userRepository, IAuth authGateway, IPasswordEncoder passwordEncoder, IOrderHistoryRepository historyRepository, ObjectMapper objectMapper) 
    {
        this.userRepository = userRepository;
        this.authGateway = authGateway;
        this.passwordEncoder = passwordEncoder;
        this.historyRepository = historyRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Registers a new customer in the system.
     */
    public Result<UserDTO> register(String username, String rawPassword) {
        logger.debug("Attempting to register new user: {}", username);

        try {
            if (userRepository.findByUsername(username).isPresent()) {
                logger.warn("Registration failed: Username '{}' is already taken.", username);
                return Result.failure("Username is already taken.");
            }

            String hashedPassword = passwordEncoder.encodePassword(rawPassword);
            
            // Assuming default new users are CUSTOMERs and are ACTIVE
            User newUser = new User(
                UUID.randomUUID().toString(), 
                username, 
                hashedPassword, 
                UserRole.MEMBER, 
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


            String jwtToken = authGateway.generateToken(user.getID());
            
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

    public Result<List<OrderHistoryDTO>> viewOrderHistory(String token) 
    {
        if(!authGateway.validateToken(token)) 
        {
            logger.warn("Unauthorized order history access attempt with invalid token.");
            return Result.failure("Unauthorized.");
        }
        String userId = authGateway.extractUserId(token);
        
        List<OrderHistory> histories = historyRepository.findByUserId(userId);

        List<OrderHistoryDTO> dtos = histories.stream()
            .map(history -> objectMapper.convertValue(history, OrderHistoryDTO.class))
            .collect(Collectors.toList());

        return Result.success(dtos);
    }
}
