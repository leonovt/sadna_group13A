package com.sadna.group13a.application.Services;

import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.DTO.RaffleDTO;
import com.sadna.group13a.application.DTO.RaffleRegistrationDTO;
import com.sadna.group13a.application.DTO.RaffleResultDTO;
import com.sadna.group13a.application.DTO.WinningTicketDTO;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.domain.Aggregates.Company.CompanyPermission;
import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import com.sadna.group13a.domain.Aggregates.Event.Event;
import com.sadna.group13a.domain.Aggregates.Event.EventSaleMode;
import com.sadna.group13a.domain.Aggregates.Raffle.AuthorizationCode;
import com.sadna.group13a.domain.Interfaces.ICompanyRepository;
import com.sadna.group13a.domain.Interfaces.IEventRepository;
import com.sadna.group13a.domain.Interfaces.IRaffleRepository;
import com.sadna.group13a.domain.Aggregates.Raffle.Raffle;
import com.sadna.group13a.domain.Events.RaffleDrawnEvent;
import com.sadna.group13a.domain.Events.RaffleWonEvent;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import com.sadna.group13a.domain.Aggregates.User.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RaffleService {

    private static final Logger logger = LoggerFactory.getLogger(RaffleService.class);

    private final IRaffleRepository raffleRepository;
    private final IEventRepository eventRepository;
    private final ICompanyRepository companyRepository;
    private final IUserRepository userRepository;
    private final IAuth authGateway;
    private final ApplicationEventPublisher eventPublisher;

    public RaffleService(IRaffleRepository raffleRepository,
                         IEventRepository eventRepository,
                         ICompanyRepository companyRepository,
                         IUserRepository userRepository,
                         IAuth authGateway,
                         ApplicationEventPublisher eventPublisher) {
        this.raffleRepository = raffleRepository;
        this.eventRepository = eventRepository;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.authGateway = authGateway;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 0. Command: Create Raffle
     * Called by the event owner when an event is set to RAFFLE sale mode.
     */
    public Result<String> createRaffle(String token, String eventId, String companyId) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized createRaffle attempt for event '{}'.", eventId);
            return Result.failure("User not authenticated.");
        }
        String actingUserId = authGateway.extractUserId(token);

        Optional<User> userOpt = userRepository.findById(actingUserId);
        if (userOpt.isEmpty() || !userOpt.get().canPurchase()) {
            logger.warn("User '{}' cannot create raffle — not an active member.", actingUserId);
            return Result.failure("Only active members can create a raffle.");
        }

        Optional<ProductionCompany> compOpt = companyRepository.findById(companyId);
        if (compOpt.isEmpty()) {
            logger.warn("User '{}' tried to create raffle but company '{}' not found.", actingUserId, companyId);
            return Result.failure("User lacks permission to manage events for this company.");
        }
        if (!compOpt.get().hasPermission(actingUserId, CompanyPermission.MANAGE_EVENTS)) {
            logger.warn("User '{}' lacks MANAGE_EVENTS permission on company '{}' — createRaffle denied.", actingUserId, companyId);
            return Result.failure("User lacks permission to manage events for this company.");
        }

        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) {
            logger.warn("User '{}' tried to create raffle for non-existent event '{}'.", actingUserId, eventId);
            return Result.failure("Event not found.");
        }

        Event event = eventOpt.get();
        try {
            event.setSaleMode(EventSaleMode.RAFFLE);
        } catch (Exception e) {
            logger.warn("User '{}' failed to set RAFFLE sale mode for event '{}': {}", actingUserId, eventId, e.getMessage());
            return Result.failure(e.getMessage());
        }

        String raffleId = UUID.randomUUID().toString();
        Raffle raffle = new Raffle(raffleId, eventId, companyId);
        raffleRepository.save(raffle);
        eventRepository.save(event);

        logger.info("User '{}' created raffle '{}' for event '{}'.", actingUserId, raffleId, eventId);
        return Result.success(raffleId);
    }

    /**
     * 0b. Command: Close Raffle
     * Permanently closes the raffle (e.g. event cancelled, or owner decision).
     */
    public Result<Void> closeRaffle(String token, String raffleId) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized closeRaffle attempt for raffle '{}'.", raffleId);
            return Result.failure("User not authenticated.");
        }
        String actingUserId = authGateway.extractUserId(token);

        Optional<Raffle> raffleOpt = raffleRepository.findById(raffleId);
        if (raffleOpt.isEmpty()) {
            logger.warn("User '{}' tried to close non-existent raffle '{}'.", actingUserId, raffleId);
            return Result.failure("Raffle not found.");
        }

        Raffle raffle = raffleOpt.get();
        Optional<ProductionCompany> compOpt = companyRepository.findById(raffle.getCompanyId());
        if (compOpt.isEmpty()) {
            logger.warn("User '{}' tried to close raffle '{}' but company '{}' not found.", actingUserId, raffleId, raffle.getCompanyId());
            return Result.failure("User lacks permission to manage events for this company.");
        }
        if (!compOpt.get().hasPermission(actingUserId, CompanyPermission.MANAGE_EVENTS)) {
            logger.warn("User '{}' lacks MANAGE_EVENTS permission — closeRaffle '{}' denied.", actingUserId, raffleId);
            return Result.failure("User lacks permission to manage events for this company.");
        }

        try {
            raffle.close();
            raffleRepository.save(raffle);
            logger.warn("User '{}' closed raffle '{}'.", actingUserId, raffleId);
            return Result.success();
        } catch (Exception e) {
            logger.error("User '{}' failed to close raffle '{}': {}", actingUserId, raffleId, e.getMessage(), e);
            return Result.failure("Failed to close raffle: " + e.getMessage());
        }
    }

    /**
     * 1. Command: Join Raffle
     */
    public Result<Void> joinRaffle(String token, RaffleRegistrationDTO requestDto) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized joinRaffle attempt for raffle '{}'.", requestDto.raffleId());
            return Result.failure("User not authenticated.");
        }
        String userId = authGateway.extractUserId(token);
        String raffleId = requestDto.raffleId();

        logger.debug("User '{}' attempting to join raffle '{}'.", userId, raffleId);

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty() || !userOpt.get().canPurchase()) {
            logger.warn("User '{}' cannot join raffle '{}' — not an active member.", userId, raffleId);
            return Result.failure("Only active registered members can join a raffle.");
        }

        Optional<Raffle> raffleOpt = raffleRepository.findById(raffleId);
        if (raffleOpt.isEmpty()) {
            logger.warn("User '{}' tried to join non-existent raffle '{}'.", userId, raffleId);
            return Result.failure("Raffle not found.");
        }

        Raffle raffle = raffleOpt.get();

        try {
            raffle.registerParticipant(userId);
            raffleRepository.save(raffle);

            logger.info("User '{}' joined raffle '{}'.", userId, raffleId);
            return Result.success();

        } catch (IllegalStateException | IllegalArgumentException e) {
            logger.warn("User '{}' failed to join raffle '{}': {}", userId, raffleId, e.getMessage());
            return Result.failure(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while user '{}' was joining raffle '{}': {}", userId, raffleId, e.getMessage(), e);
            return Result.failure("An unexpected internal error occurred.");
        }
    }
    
    /**
     * 2. Command: Execute Draw
     */
    public Result<RaffleResultDTO> drawWinners(String token, String raffleId, int winnersCount, int validMinutes) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized drawWinners attempt for raffle '{}'.", raffleId);
            return Result.failure("User not authenticated.");
        }
        String actingUserId = authGateway.extractUserId(token);

        Optional<Raffle> raffleOpt = raffleRepository.findById(raffleId);
        if (raffleOpt.isEmpty()) {
            logger.warn("User '{}' tried to draw winners for non-existent raffle '{}'.", actingUserId, raffleId);
            return Result.failure("Raffle not found.");
        }

        Raffle raffle = raffleOpt.get();
        Optional<ProductionCompany> compOpt = companyRepository.findById(raffle.getCompanyId());
        if (compOpt.isEmpty()) {
            logger.warn("User '{}' tried to draw winners for raffle '{}' but company '{}' not found.", actingUserId, raffleId, raffle.getCompanyId());
            return Result.failure("User lacks permission to manage events for this company.");
        }
        if (!compOpt.get().hasPermission(actingUserId, CompanyPermission.MANAGE_EVENTS)) {
            logger.warn("User '{}' lacks MANAGE_EVENTS permission — drawWinners for raffle '{}' denied.", actingUserId, raffleId);
            return Result.failure("User lacks permission to manage events for this company.");
        }

        try {
            raffle.executeDraw(winnersCount, validMinutes);
            raffleRepository.save(raffle);

            int actuallyDrawn = raffle.getWinningCodes().size();
            // Individual notification per winner with their unique auth code
            for (AuthorizationCode code : raffle.getWinningCodes()) {
                eventPublisher.publishEvent(new RaffleWonEvent(
                        code.getUserId(), raffle.getEventId(), code.getCode(), code.getExpirationTime()));
            }
            // Notify all participants that the raffle has been drawn
            List<String> allParticipants = new java.util.ArrayList<>(raffle.getParticipantUserIds());
            eventPublisher.publishEvent(new RaffleDrawnEvent(raffleId, raffle.getEventId(), actuallyDrawn, allParticipants));
            logger.info("User '{}' executed draw for raffle '{}' — {} winner(s) selected.", actingUserId, raffleId, actuallyDrawn);

            return Result.success(new RaffleResultDTO(raffle.getId(), "Draw executed successfully.", actuallyDrawn));

        } catch (IllegalStateException e) {
            logger.warn("User '{}' failed to draw winners for raffle '{}': {}", actingUserId, raffleId, e.getMessage());
            return Result.failure(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during draw for raffle '{}' by user '{}': {}", raffleId, actingUserId, e.getMessage(), e);
            return Result.failure("An unexpected internal error occurred.");
        }
    }

    /**
     * 2b. Query: Find the raffle linked to an event — used by the event detail page.
     */
    public Result<RaffleDTO> getRaffleByEventId(String token, String eventId) {
        if (!authGateway.validateToken(token)) {
            return Result.failure("User not authenticated.");
        }
        List<Raffle> raffles = raffleRepository.findByEventId(eventId);
        if (raffles.isEmpty()) {
            return Result.failure("No raffle found for this event.");
        }
        Raffle raffle = raffles.get(0);
        return Result.success(new RaffleDTO(
            raffle.getId(), raffle.getEventId(), raffle.getCompanyId(),
            raffle.getStatus(), raffle.getParticipantUserIds().size()));
    }

    /**
     * 3. Query: Get General Status
     */
    public Result<RaffleDTO> getRaffleDetails(String token, String raffleId) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized getRaffleDetails attempt for raffle '{}'.", raffleId);
            return Result.failure("User not authenticated.");
        }
        String callerId = authGateway.extractUserId(token);

        Optional<Raffle> raffleOpt = raffleRepository.findById(raffleId);
        if (raffleOpt.isEmpty()) {
            logger.warn("getRaffleDetails: raffle '{}' not found (caller='{}').", raffleId, callerId);
            return Result.failure("Raffle not found.");
        }

        Raffle raffle = raffleOpt.get();

        RaffleDTO dto = new RaffleDTO(
            raffle.getId(),
            raffle.getEventId(),
            raffle.getCompanyId(),
            raffle.getStatus(),
            raffle.getParticipantUserIds().size()
        );

        logger.debug("getRaffleDetails: raffle '{}' retrieved by '{}' ({} participant(s)).", raffleId, callerId, dto.totalParticipants());
        return Result.success(dto);
    }

    /**
     * 4. Query: Check Winning Status
     */
    public Result<WinningTicketDTO> checkMyResult(String token, String raffleId) {
        if (!authGateway.validateToken(token)) {
            logger.warn("Unauthorized checkMyResult attempt for raffle '{}'.", raffleId);
            return Result.failure("User not authenticated.");
        }
        String userId = authGateway.extractUserId(token);

        Optional<Raffle> raffleOpt = raffleRepository.findById(raffleId);
        if (raffleOpt.isEmpty()) {
            logger.warn("checkMyResult: raffle '{}' not found (caller='{}').", raffleId, userId);
            return Result.failure("Raffle not found.");
        }

        Raffle raffle = raffleOpt.get();

        Optional<AuthorizationCode> codeOpt = raffle.getAuthorizationCodeFor(userId);

        if (codeOpt.isEmpty()) {
            logger.debug("checkMyResult: user '{}' did not win raffle '{}' (or draw not yet run).", userId, raffleId);
            return Result.failure("You did not win this raffle, or the draw hasn't happened yet.");
        }

        AuthorizationCode code = codeOpt.get();

        try {
            WinningTicketDTO dto = new WinningTicketDTO(
                code.getEventId(),
                code.getCode(),
                code.getExpirationTime()
            );
            logger.debug("checkMyResult: user '{}' is a winner of raffle '{}'.", userId, raffleId);
            return Result.success(dto);

        } catch (Exception e) {
            logger.error("Failed to map AuthorizationCode to DTO for user '{}' raffle '{}': {}", userId, raffleId, e.getMessage(), e);
            return Result.failure("Internal mapping error.");
        }
    }

    public Result<List<RaffleDTO>> getRafflesForUser(String token) {
        if (!authGateway.validateToken(token)) {
            return Result.failure("User not authenticated.");
        }
        String userId = authGateway.extractUserId(token);
        List<RaffleDTO> raffles = raffleRepository.findByUserId(userId).stream()
                .filter(r -> r.getStatus() != com.sadna.group13a.domain.Aggregates.Raffle.RaffleStatus.CLOSED)
                .map(r -> new RaffleDTO(r.getId(), r.getEventId(), r.getCompanyId(), r.getStatus(), r.getParticipantUserIds().size()))
                .collect(java.util.stream.Collectors.toList());
        return Result.success(raffles);
    }

    public Result<List<RaffleDTO>> getRafflesForCompany(String token, String companyId) {
        if (!authGateway.validateToken(token)) {
            return Result.failure("User not authenticated.");
        }
        String actingUserId = authGateway.extractUserId(token);
        Optional<ProductionCompany> compOpt = companyRepository.findById(companyId);
        if (compOpt.isEmpty()) {
            return Result.failure("Company not found.");
        }
        if (!compOpt.get().hasPermission(actingUserId, CompanyPermission.MANAGE_EVENTS)) {
            return Result.failure("User lacks permission to manage events for this company.");
        }
        List<RaffleDTO> raffles = raffleRepository.findByCompanyId(companyId).stream()
                .map(r -> new RaffleDTO(r.getId(), r.getEventId(), r.getCompanyId(), r.getStatus(), r.getParticipantUserIds().size()))
                .collect(java.util.stream.Collectors.toList());
        return Result.success(raffles);
    }
}