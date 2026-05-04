package com.sadna.group13a.application.Services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sadna.group13a.application.DTO.RaffleRegistrationDTO;
import com.sadna.group13a.application.DTO.RaffleResultDTO;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.domain.Aggregates.Raffle.Raffle;
import com.sadna.group13a.domain.Aggregates.User.Member;
import com.sadna.group13a.domain.Events.RaffleDrawnEvent;
import com.sadna.group13a.domain.Interfaces.IRaffleRepository;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RaffleServiceTest {

    @Mock private IRaffleRepository raffleRepository;
    @Mock private IUserRepository userRepository;
    @Mock private IAuth authGateway;
    @Mock private ObjectMapper objectMapper;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private RaffleService raffleService;

    private static final String TOKEN   = "valid-token";
    private static final String USER_ID = "user-1";
    private static final String RAFFLE_ID = "raffle-1";

    private Member activeUser;
    private Raffle raffle;

    @BeforeEach
    void setUp() {
        activeUser = new Member(USER_ID, "alice", "hash");
        raffle = new Raffle(RAFFLE_ID, "event-1", "co-1");

        lenient().when(authGateway.validateToken(TOKEN)).thenReturn(true);
        lenient().when(authGateway.extractUserId(TOKEN)).thenReturn(USER_ID);
    }

    // ── createRaffle ──────────────────────────────────────────────

    @Test
    void givenInvalidToken_whenCreateRaffle_thenReturnsFailure() {
        when(authGateway.validateToken("bad")).thenReturn(false);

        assertFalse(raffleService.createRaffle("bad", "event-1", "co-1").isSuccess());
        verify(raffleRepository, never()).save(any());
    }

    @Test
    void givenInactiveUser_whenCreateRaffle_thenReturnsFailure() {
        activeUser.deactivate();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser));

        assertFalse(raffleService.createRaffle(TOKEN, "event-1", "co-1").isSuccess());
        verify(raffleRepository, never()).save(any());
    }

    @Test
    void givenActiveUser_whenCreateRaffle_thenRaffleSavedAndIdReturned() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser));

        Result<String> result = raffleService.createRaffle(TOKEN, "event-1", "co-1");

        assertTrue(result.isSuccess());
        assertNotNull(result.getData().get());
        verify(raffleRepository).save(any(Raffle.class));
    }

    // ── joinRaffle ────────────────────────────────────────────────

    @Test
    void givenInvalidToken_whenJoinRaffle_thenReturnsFailure() {
        when(authGateway.validateToken("bad")).thenReturn(false);

        assertFalse(raffleService.joinRaffle("bad", new RaffleRegistrationDTO(RAFFLE_ID)).isSuccess());
    }

    @Test
    void givenInactiveUser_whenJoinRaffle_thenReturnsFailure() {
        activeUser.deactivate();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser));

        assertFalse(raffleService.joinRaffle(TOKEN, new RaffleRegistrationDTO(RAFFLE_ID)).isSuccess());
        verify(raffleRepository, never()).save(any());
    }

    @Test
    void givenRaffleNotFound_whenJoinRaffle_thenReturnsFailure() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser));
        when(raffleRepository.findById(RAFFLE_ID)).thenReturn(Optional.empty());

        assertFalse(raffleService.joinRaffle(TOKEN, new RaffleRegistrationDTO(RAFFLE_ID)).isSuccess());
    }

    @Test
    void givenOpenRaffle_whenJoinRaffle_thenParticipantRegisteredAndRaffleSaved() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser));
        when(raffleRepository.findById(RAFFLE_ID)).thenReturn(Optional.of(raffle));

        Result<Void> result = raffleService.joinRaffle(TOKEN, new RaffleRegistrationDTO(RAFFLE_ID));

        assertTrue(result.isSuccess());
        assertTrue(raffle.getParticipantUserIds().contains(USER_ID));
        verify(raffleRepository).save(raffle);
    }

    @Test
    void givenAlreadyJoinedRaffle_whenJoinRaffleAgain_thenReturnsFailure() {
        raffle.registerParticipant(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser));
        when(raffleRepository.findById(RAFFLE_ID)).thenReturn(Optional.of(raffle));

        Result<Void> result = raffleService.joinRaffle(TOKEN, new RaffleRegistrationDTO(RAFFLE_ID));

        assertFalse(result.isSuccess());
    }

    // ── drawWinners ───────────────────────────────────────────────

    @Test
    void givenInvalidToken_whenDrawWinners_thenReturnsFailure() {
        when(authGateway.validateToken("bad")).thenReturn(false);

        assertFalse(raffleService.drawWinners("bad", RAFFLE_ID, 3, 60).isSuccess());
    }

    @Test
    void givenRaffleNotFound_whenDrawWinners_thenReturnsFailure() {
        when(raffleRepository.findById(RAFFLE_ID)).thenReturn(Optional.empty());

        assertFalse(raffleService.drawWinners(TOKEN, RAFFLE_ID, 3, 60).isSuccess());
    }

    @Test
    void givenRaffleWithParticipants_whenDrawWinners_thenRaffleSavedAndEventPublished() {
        raffle.registerParticipant("user-A");
        raffle.registerParticipant("user-B");
        raffle.registerParticipant("user-C");
        when(raffleRepository.findById(RAFFLE_ID)).thenReturn(Optional.of(raffle));

        Result<RaffleResultDTO> result = raffleService.drawWinners(TOKEN, RAFFLE_ID, 2, 60);

        assertTrue(result.isSuccess());
        assertEquals(2, result.getData().get().expectedWinnersDrawn());
        verify(raffleRepository).save(raffle);
        verify(eventPublisher).publishEvent(any(RaffleDrawnEvent.class));
    }

    @Test
    void givenAlreadyDrawnRaffle_whenDrawWinnersAgain_thenReturnsFailure() {
        raffle.registerParticipant("user-A");
        raffle.executeDraw(1, 60);
        when(raffleRepository.findById(RAFFLE_ID)).thenReturn(Optional.of(raffle));

        Result<RaffleResultDTO> result = raffleService.drawWinners(TOKEN, RAFFLE_ID, 1, 60);

        assertFalse(result.isSuccess());
    }

    // ── getRaffleDetails ──────────────────────────────────────────

    @Test
    void givenInvalidToken_whenGetRaffleDetails_thenReturnsFailure() {
        when(authGateway.validateToken("bad")).thenReturn(false);

        assertFalse(raffleService.getRaffleDetails("bad", RAFFLE_ID).isSuccess());
    }

    @Test
    void givenRaffleNotFound_whenGetRaffleDetails_thenReturnsFailure() {
        when(raffleRepository.findById(RAFFLE_ID)).thenReturn(Optional.empty());

        assertFalse(raffleService.getRaffleDetails(TOKEN, RAFFLE_ID).isSuccess());
    }

    // ── checkMyResult ─────────────────────────────────────────────

    @Test
    void givenNonWinner_whenCheckMyResult_thenReturnsFailure() {
        raffle.registerParticipant("other-user");
        raffle.executeDraw(1, 60);
        when(raffleRepository.findById(RAFFLE_ID)).thenReturn(Optional.of(raffle));

        Result<?> result = raffleService.checkMyResult(TOKEN, RAFFLE_ID);

        // USER_ID never entered, so no code exists for them
        assertFalse(result.isSuccess());
    }
}
