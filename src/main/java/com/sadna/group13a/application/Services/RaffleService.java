package com.sadna.group13a.application.Services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.DTO.RaffleDTO;
import com.sadna.group13a.application.DTO.RaffleRegistrationDTO;
import com.sadna.group13a.application.DTO.RaffleResultDTO;
import com.sadna.group13a.application.DTO.WinningTicketDTO;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.domain.Aggregates.Raffle.AuthorizationCode;
import com.sadna.group13a.domain.Interfaces.IRaffleRepository;
import com.sadna.group13a.domain.Aggregates.Raffle.Raffle;
import com.sadna.group13a.domain.Events.RaffleDrawnEvent;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import com.sadna.group13a.domain.Aggregates.User.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class RaffleService {

    private static final Logger logger = LoggerFactory.getLogger(RaffleService.class);

    private final IRaffleRepository raffleRepository;
    private final IUserRepository userRepository;
    private final IAuth authGateway;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    public RaffleService(IRaffleRepository raffleRepository, IUserRepository userRepository,
                         IAuth authGateway, ObjectMapper objectMapper,
                         ApplicationEventPublisher eventPublisher) {
        this.raffleRepository = raffleRepository;
        this.userRepository = userRepository;
        this.authGateway = authGateway;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 0. Command: Create Raffle
     * Called by the event owner when an event is set to RAFFLE sale mode.
     */
    public Result<String> createRaffle(String token, String eventId, String companyId) {
        if (!authGateway.validateToken(token)) {
            return Result.failure("User not authenticated.");
        }
        String actingUserId = authGateway.extractUserId(token);

        // Verify the user is an active member before allowing raffle creation
        Optional<User> userOpt = userRepository.findById(actingUserId);
        if (userOpt.isEmpty() || !userOpt.get().isActive()) {
            return Result.failure("Only active members can create a raffle.");
        }

        String raffleId = UUID.randomUUID().toString();
        Raffle raffle = new Raffle(raffleId, eventId, companyId);
        raffleRepository.save(raffle);

        logger.info("User {} created raffle {} for event {}.", actingUserId, raffleId, eventId);
        return Result.success(raffleId);
    }

    /**
     * 0b. Command: Close Raffle
     * Permanently closes the raffle (e.g. event cancelled, or owner decision).
     */
    public Result<Void> closeRaffle(String token, String raffleId) {
        if (!authGateway.validateToken(token)) {
            return Result.failure("User not authenticated.");
        }
        String actingUserId = authGateway.extractUserId(token);

        Optional<Raffle> raffleOpt = raffleRepository.findById(raffleId);
        if (raffleOpt.isEmpty()) {
            return Result.failure("Raffle not found.");
        }

        Raffle raffle = raffleOpt.get();
        try {
            raffle.close();
            raffleRepository.save(raffle);
            logger.info("User {} closed raffle {}.", actingUserId, raffleId);
            return Result.success();
        } catch (Exception e) {
            logger.error("Failed to close raffle {}: {}", raffleId, e.getMessage());
            return Result.failure("Failed to close raffle: " + e.getMessage());
        }
    }

    /**
     * 1. Command: Join Raffle
     */
    public Result<Void> joinRaffle(String token, RaffleRegistrationDTO requestDto) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized attempt to join raffle with invalid token.");
            return Result.failure("User not authenticated.");
        }
        String userId = authGateway.extractUserId(token);
        String raffleId = requestDto.raffleId();
        
        logger.debug("User {} is attempting to join raffle {}", userId, raffleId);

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty() || !userOpt.get().isActive()) {
            return Result.failure("Only active registered members can join a raffle.");
        }

        Optional<Raffle> raffleOpt = raffleRepository.findById(raffleId);
        if (raffleOpt.isEmpty()) {
            return Result.failure("Raffle not found.");
        }

        Raffle raffle = raffleOpt.get();

        try {
            raffle.registerParticipant(userId);
            raffleRepository.save(raffle);
            
            logger.info("User {} successfully joined raffle {}", userId, raffleId);
            return Result.success();
            
        } catch (IllegalStateException | IllegalArgumentException e) {
            return Result.failure(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while user {} was joining raffle {}", userId, raffleId, e);
            return Result.failure("An unexpected internal error occurred.");
        }
    }
    
    /**
     * 2. Command: Execute Draw
     */
    public Result<RaffleResultDTO> drawWinners(String token, String raffleId, int winnersCount, int validMinutes) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized attempt to draw winners for raffle {} with invalid token.", raffleId);
            return Result.failure("User not authenticated.");
        }
        String actingUserId = authGateway.extractUserId(token);
        
        Optional<Raffle> raffleOpt = raffleRepository.findById(raffleId);
        if (raffleOpt.isEmpty()) {
            return Result.failure("Raffle not found.");
        }

        Raffle raffle = raffleOpt.get();

        try {
            // 1. The Aggregate handles all the complex code generation internally!
            raffle.executeDraw(winnersCount, validMinutes);
            raffleRepository.save(raffle);

            int actuallyDrawn = raffle.getWinningCodes().size();
            eventPublisher.publishEvent(new RaffleDrawnEvent(raffleId, raffle.getEventId(), actuallyDrawn));
            logger.info("User {} successfully executed draw for raffle {}.", actingUserId, raffleId);
            
            RaffleResultDTO responseDto = new RaffleResultDTO(
                raffle.getId(),
                "Draw executed successfully.",
                actuallyDrawn
            );
            
            return Result.success(responseDto);
            
        } catch (IllegalStateException e) {
            return Result.failure(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during draw execution for raffle {}", raffleId, e);
            return Result.failure("An unexpected internal error occurred.");
        }
    }

    /**
     * 3. Query: Get General Status
     */
    public Result<RaffleDTO> getRaffleDetails(String token, String raffleId) {
        if (!authGateway.validateToken(token)) {
            return Result.failure("User not authenticated.");
        }

        Optional<Raffle> raffleOpt = raffleRepository.findById(raffleId);
        if (raffleOpt.isEmpty()) {
            return Result.failure("Raffle not found.");
        }

        Raffle raffle = raffleOpt.get();

        try {
            RaffleDTO baseDto = objectMapper.convertValue(raffle, RaffleDTO.class);
            
            // Inject the manual calculation for privacy (so we don't expose the user IDs list)
            RaffleDTO finalDto = new RaffleDTO(
                baseDto.id(),
                baseDto.eventId(),
                baseDto.companyId(),
                baseDto.status(),
                raffle.getParticipantUserIds().size() 
            );
            
            return Result.success(finalDto);
            
        } catch (Exception e) {
            logger.error("Failed to map Raffle to DTO using ObjectMapper", e);
            return Result.failure("Internal mapping error.");
        }
    }

    /**
     * 4. Query: Check Winning Status
     */
    public Result<WinningTicketDTO> checkMyResult(String token, String raffleId) {
        if (!authGateway.validateToken(token)) {
            return Result.failure("User not authenticated.");
        }
        String userId = authGateway.extractUserId(token);

        Optional<Raffle> raffleOpt = raffleRepository.findById(raffleId);
        if (raffleOpt.isEmpty()) {
            return Result.failure("Raffle not found.");
        }

        Raffle raffle = raffleOpt.get();
        
        // The service asks the Aggregate for the domain object...
        Optional<AuthorizationCode> codeOpt = raffle.getAuthorizationCodeFor(userId);

        if (codeOpt.isEmpty()) {
            return Result.failure("You did not win this raffle, or the draw hasn't happened yet.");
        }

        AuthorizationCode code = codeOpt.get();

        try {
            // ... and immediately translates it into a DTO so the pure Domain Object never reaches the Controller.
            WinningTicketDTO dto = objectMapper.convertValue(code, WinningTicketDTO.class);
            return Result.success(dto);
            
        } catch (Exception e) {
            logger.error("Failed to map AuthorizationCode to DTO using ObjectMapper", e);
            return Result.failure("Internal mapping error.");
        }
    }
}