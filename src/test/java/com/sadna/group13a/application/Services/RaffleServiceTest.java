package com.sadna.group13a.application.Services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sadna.group13a.application.DTO.RaffleRegistrationDTO;
import com.sadna.group13a.application.DTO.RaffleResultDTO;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import com.sadna.group13a.domain.Aggregates.Event.Event;
import com.sadna.group13a.domain.Aggregates.Raffle.Raffle;
import com.sadna.group13a.domain.Aggregates.User.Member;
import com.sadna.group13a.domain.Events.RaffleDrawnEvent;
import com.sadna.group13a.domain.Interfaces.ICompanyRepository;
import com.sadna.group13a.domain.Interfaces.IEventRepository;
import com.sadna.group13a.domain.Interfaces.IRaffleRepository;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RaffleServiceTest {

    @Mock private IRaffleRepository raffleRepository;
    @Mock private IEventRepository eventRepository;
    @Mock private ICompanyRepository companyRepository;
    @Mock private IUserRepository userRepository;
    @Mock private IAuth authGateway;
    @Mock private ObjectMapper objectMapper;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private RaffleService raffleService;

    private static final String TOKEN     = "valid-token";
    private static final String USER_ID   = "user-1";
    private static final String RAFFLE_ID = "raffle-1";
    private static final String EVENT_ID  = "event-1";
    private static final String COMPANY_ID = "co-1";

    private Member activeUser;
    private Raffle raffle;
    private ProductionCompany company;
    private Event event;

    @BeforeEach
    void setUp() {
        activeUser = new Member(USER_ID, "alice", "hash");
        raffle = new Raffle(RAFFLE_ID, EVENT_ID, COMPANY_ID);
        company = new ProductionCompany(COMPANY_ID, "Acme", "Desc", USER_ID); // USER_ID is founder
        event = new Event(EVENT_ID, "Test Event", "Desc", COMPANY_ID,
                java.time.LocalDateTime.now().plusDays(30), "Music");

        lenient().when(authGateway.validateToken(TOKEN)).thenReturn(true);
        lenient().when(authGateway.extractUserId(TOKEN)).thenReturn(USER_ID);
        lenient().when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        lenient().when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        lenient().when(raffleRepository.findById(RAFFLE_ID)).thenReturn(Optional.of(raffle));
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

        assertFalse(raffleService.createRaffle(TOKEN, EVENT_ID, COMPANY_ID).isSuccess());
        verify(raffleRepository, never()).save(any());
    }

    @Test
    void givenUserWithoutPermission_whenCreateRaffle_thenReturnsFailure() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser));
        ProductionCompany otherCompany = new ProductionCompany("co-other", "Other", "Desc", "someone-else");
        when(companyRepository.findById("co-other")).thenReturn(Optional.of(otherCompany));

        assertFalse(raffleService.createRaffle(TOKEN, EVENT_ID, "co-other").isSuccess());
        verify(raffleRepository, never()).save(any());
    }

    @Test
    void givenActiveUserWithPermission_whenCreateRaffle_thenRaffleSavedAndSaleModeSet() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser));

        Result<String> result = raffleService.createRaffle(TOKEN, EVENT_ID, COMPANY_ID);

        assertTrue(result.isSuccess());
        assertNotNull(result.getData().get());
        verify(raffleRepository).save(any(Raffle.class));
        verify(eventRepository).save(event);
        assertEquals(com.sadna.group13a.domain.Aggregates.Event.EventSaleMode.RAFFLE, event.getSaleMode());
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
        assertFalse(raffleService.getRaffleDetails(TOKEN, "unknown-raffle").isSuccess());
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

    // ── getRafflesForUser ──────────────────────────────────────────

    @Test
    void givenUserWithOpenRaffle_whenGetRafflesForUser_thenReturnsIt() {
        raffle.registerParticipant(USER_ID);
        when(raffleRepository.findByUserId(USER_ID)).thenReturn(List.of(raffle));

        Result<?> result = raffleService.getRafflesForUser(TOKEN);

        assertTrue(result.isSuccess());
    }

    @Test
    void givenInvalidToken_whenGetRafflesForUser_thenReturnsFailure() {
        when(authGateway.validateToken("bad")).thenReturn(false);

        assertFalse(raffleService.getRafflesForUser("bad").isSuccess());
    }

    // ── getRafflesForCompany ───────────────────────────────────────

    @Test
    void givenOwner_whenGetRafflesForCompany_thenReturnsList() {
        when(raffleRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of(raffle));

        Result<?> result = raffleService.getRafflesForCompany(TOKEN, COMPANY_ID);

        assertTrue(result.isSuccess());
    }

    @Test
    void givenNonMember_whenGetRafflesForCompany_thenReturnsFailure() {
        ProductionCompany otherCompany = new ProductionCompany("other-co", "Other", "Desc", "other-owner");
        when(companyRepository.findById("other-co")).thenReturn(Optional.of(otherCompany));

        assertFalse(raffleService.getRafflesForCompany(TOKEN, "other-co").isSuccess());
    }

    @Test
    void givenInvalidToken_whenGetRafflesForCompany_thenReturnsFailure() {
        when(authGateway.validateToken("bad")).thenReturn(false);

        assertFalse(raffleService.getRafflesForCompany("bad", COMPANY_ID).isSuccess());
    }
}
