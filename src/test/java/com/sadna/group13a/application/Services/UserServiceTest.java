package com.sadna.group13a.application.Services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sadna.group13a.application.DTO.OrderHistoryDTO;
import com.sadna.group13a.application.DTO.UserDTO;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Interfaces.IPasswordEncoder;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.domain.Aggregates.OrderHistory.OrderHistory;
import com.sadna.group13a.domain.Aggregates.OrderHistory.OrderHistoryItem;
import com.sadna.group13a.domain.Aggregates.User.Guest;
import com.sadna.group13a.domain.Aggregates.User.Member;
import com.sadna.group13a.domain.Aggregates.User.User;
import com.sadna.group13a.domain.Aggregates.User.UserRole;
import com.sadna.group13a.domain.Interfaces.IOrderHistoryRepository;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private IUserRepository userRepository;
    @Mock private IAuth authGateway;
    @Mock private IPasswordEncoder passwordEncoder;
    @Mock private IOrderHistoryRepository historyRepository;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private UserService userService;

    private static final String TOKEN   = "valid-token";
    private static final String USER_ID = "user-1";

    // ── register ──────────────────────────────────────────────────

    @Test
    void givenNewUsername_whenRegister_thenUserSavedAndDtoReturned() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(passwordEncoder.encodePassword("pass")).thenReturn("hashed");

        Result<UserDTO> result = userService.register("alice", "pass");

        assertTrue(result.isSuccess());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void givenExistingUsername_whenRegister_thenReturnsFailure() {
        when(userRepository.findByUsername("alice"))
                .thenReturn(Optional.of(new Member(UUID.randomUUID().toString(), "alice", "hash")));

        Result<UserDTO> result = userService.register("alice", "pass");

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("already taken"));
        verify(userRepository, never()).save(any());
    }

    // ── login ─────────────────────────────────────────────────────

    @Test
    void givenValidCredentials_whenLogin_thenReturnsToken() {
        Member user = new Member(USER_ID, "alice", "hashed");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pass", "hashed")).thenReturn(true);
        when(authGateway.generateToken(USER_ID)).thenReturn("jwt-token");

        Result<String> result = userService.login("alice", "pass");

        assertTrue(result.isSuccess());
        assertEquals("jwt-token", result.getData().get());
    }

    @Test
    void givenNonExistentUsername_whenLogin_thenReturnsFailure() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        Result<String> result = userService.login("ghost", "pass");

        assertFalse(result.isSuccess());
    }

    @Test
    void givenWrongPassword_whenLogin_thenReturnsFailure() {
        Member user = new Member(USER_ID, "alice", "hashed");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        Result<String> result = userService.login("alice", "wrong");

        assertFalse(result.isSuccess());
        verify(authGateway, never()).generateToken(any());
    }

    // ── getUserProfile ────────────────────────────────────────────

    @Test
    void givenInvalidToken_whenGetUserProfile_thenReturnsFailure() {
        when(authGateway.validateToken("bad-token")).thenReturn(false);

        Result<UserDTO> result = userService.getUserProfile("bad-token");

        assertFalse(result.isSuccess());
        assertEquals("Unauthorized.", result.getErrorMessage());
    }

    @Test
    void givenValidTokenAndUserExists_whenGetUserProfile_thenReturnsDtoSuccessfully() {
        Member user = new Member(USER_ID, "alice", "hashed");
        when(authGateway.validateToken(TOKEN)).thenReturn(true);
        when(authGateway.extractUserId(TOKEN)).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        Result<UserDTO> result = userService.getUserProfile(TOKEN);

        assertTrue(result.isSuccess());
        assertEquals("alice", result.getData().get().username());
    }

    @Test
    void givenValidTokenButUserNotFound_whenGetUserProfile_thenReturnsFailure() {
        when(authGateway.validateToken(TOKEN)).thenReturn(true);
        when(authGateway.extractUserId(TOKEN)).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        Result<UserDTO> result = userService.getUserProfile(TOKEN);

        assertFalse(result.isSuccess());
    }

    // ── logout ────────────────────────────────────────────────────

    @Test
    void givenValidToken_whenLogout_thenSucceedsAndReturnsGuestToken() {
        when(authGateway.validateToken(TOKEN)).thenReturn(true);
        when(authGateway.extractUserId(TOKEN)).thenReturn(USER_ID);
        when(authGateway.generateToken(anyString())).thenReturn("guest-token");

        Result<String> result = userService.logout(TOKEN);

        assertTrue(result.isSuccess());
        assertEquals("guest-token", result.getOrThrow());
        verify(userRepository, never()).save(any());
    }

    @Test
    void givenInvalidToken_whenLogout_thenReturnsFailure() {
        when(authGateway.validateToken("bad")).thenReturn(false);

        assertFalse(userService.logout("bad").isSuccess());
    }

    // ── updateProfile ─────────────────────────────────────────────

    @Test
    void givenInvalidToken_whenUpdateProfile_thenReturnsFailure() {
        when(authGateway.validateToken("bad")).thenReturn(false);

        assertFalse(userService.updateProfile("bad", "newname").isSuccess());
        verify(userRepository, never()).save(any());
    }

    @Test
    void givenUsernameTaken_whenUpdateProfile_thenReturnsFailure() {
        when(authGateway.validateToken(TOKEN)).thenReturn(true);
        when(authGateway.extractUserId(TOKEN)).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(new Member(USER_ID, "alice", "hash")));
        when(userRepository.findByUsername("taken"))
                .thenReturn(Optional.of(new Member("u-2", "taken", "hash")));

        Result<UserDTO> result = userService.updateProfile(TOKEN, "taken");

        assertFalse(result.isSuccess());
        verify(userRepository, never()).save(any());
    }

    @Test
    void givenAvailableUsername_whenUpdateProfile_thenUserSaved() {
        Member user = new Member(USER_ID, "alice", "hash");
        when(authGateway.validateToken(TOKEN)).thenReturn(true);
        when(authGateway.extractUserId(TOKEN)).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.findByUsername("newname")).thenReturn(Optional.empty());

        Result<UserDTO> result = userService.updateProfile(TOKEN, "newname");

        assertTrue(result.isSuccess());
        verify(userRepository).save(user);
    }

    // ── updateProfile: guest blocked ──────────────────────────────

    @Test
    void givenGuestToken_whenUpdateProfile_thenFailsAndNothingSaved() {
        when(authGateway.validateToken(TOKEN)).thenReturn(true);
        when(authGateway.extractUserId(TOKEN)).thenReturn("guest-123");
        when(userRepository.findById("guest-123")).thenReturn(Optional.empty());

        Result<UserDTO> result = userService.updateProfile(TOKEN, "newname");

        assertFalse(result.isSuccess());
        verify(userRepository, never()).save(any());
    }

    // ── updateBirthDate ───────────────────────────────────────────

    @Test
    void givenInvalidToken_whenUpdateBirthDate_thenReturnsFailure() {
        when(authGateway.validateToken("bad")).thenReturn(false);

        assertFalse(userService.updateBirthDate("bad", LocalDate.of(1995, 1, 1)).isSuccess());
        verify(userRepository, never()).save(any());
    }

    @Test
    void givenUserNotFound_whenUpdateBirthDate_thenReturnsFailure() {
        when(authGateway.validateToken(TOKEN)).thenReturn(true);
        when(authGateway.extractUserId(TOKEN)).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertFalse(userService.updateBirthDate(TOKEN, LocalDate.of(1995, 1, 1)).isSuccess());
        verify(userRepository, never()).save(any());
    }

    @Test
    void givenValidMember_whenUpdateBirthDate_thenBirthDateSavedAndReturnedInDto() {
        Member member = new Member(USER_ID, "alice", "hash");
        when(authGateway.validateToken(TOKEN)).thenReturn(true);
        when(authGateway.extractUserId(TOKEN)).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(member));

        LocalDate dob = LocalDate.of(1995, 6, 15);
        Result<UserDTO> result = userService.updateBirthDate(TOKEN, dob);

        assertTrue(result.isSuccess());
        assertEquals(dob, result.getOrThrow().dateOfBirth());
        verify(userRepository).save(member);
    }

    @Test
    void givenFutureBirthDate_whenUpdateBirthDate_thenReturnsFailure() {
        Member member = new Member(USER_ID, "alice", "hash");
        when(authGateway.validateToken(TOKEN)).thenReturn(true);
        when(authGateway.extractUserId(TOKEN)).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(member));

        Result<UserDTO> result = userService.updateBirthDate(TOKEN, LocalDate.now().plusDays(1));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("future"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void givenNullBirthDate_whenUpdateBirthDate_thenClearsBirthDate() {
        Member member = new Member(USER_ID, "alice", "hash");
        member.setDateOfBirth(LocalDate.of(1990, 1, 1));
        when(authGateway.validateToken(TOKEN)).thenReturn(true);
        when(authGateway.extractUserId(TOKEN)).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(member));

        Result<UserDTO> result = userService.updateBirthDate(TOKEN, null);

        assertTrue(result.isSuccess());
        assertNull(result.getOrThrow().dateOfBirth());
        verify(userRepository).save(member);
    }

    // ── viewOrderHistory ──────────────────────────────────────────

    @Test
    void givenInvalidToken_whenViewOrderHistory_thenReturnsFailure() {
        when(authGateway.validateToken("bad")).thenReturn(false);

        assertFalse(userService.viewOrderHistory("bad").isSuccess());
    }

    @Test
    void givenGuestToken_whenViewOrderHistory_thenFailsWithMembersOnlyMessage() {
        when(authGateway.validateToken(TOKEN)).thenReturn(true);
        when(authGateway.extractUserId(TOKEN)).thenReturn("guest-123");
        when(userRepository.findById("guest-123")).thenReturn(Optional.empty());

        Result<List<OrderHistoryDTO>> result = userService.viewOrderHistory(TOKEN);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("registered members"));
        verify(historyRepository, never()).findByUserId(any());
    }

    @Test
    void givenValidToken_whenViewOrderHistory_thenReturnsMappedDtos() {
        OrderHistoryItem item = new OrderHistoryItem(
                "ev-1", "Concert", LocalDateTime.now().plusDays(5),
                "co-1", "Acme", "VIP", "A-1", 100.0);
        OrderHistory history = new OrderHistory(
                UUID.randomUUID().toString(), USER_ID, LocalDateTime.now(), 100.0, List.of(item));

        when(authGateway.validateToken(TOKEN)).thenReturn(true);
        when(authGateway.extractUserId(TOKEN)).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(new Member(USER_ID, "alice", "hash")));
        when(historyRepository.findByUserId(USER_ID)).thenReturn(List.of(history));
        when(objectMapper.convertValue(any(OrderHistory.class), eq(OrderHistoryDTO.class)))
                .thenReturn(mock(OrderHistoryDTO.class));

        Result<List<OrderHistoryDTO>> result = userService.viewOrderHistory(TOKEN);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getData().get().size());
    }
}
