package com.sadna.group13a.application.Services;

import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Interfaces.IPasswordEncoder;
import com.sadna.group13a.application.Interfaces.IPaymentGateway;
import com.sadna.group13a.application.Interfaces.ITicketSupplier;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.domain.Aggregates.Admin.Admin;
import com.sadna.group13a.domain.Aggregates.User.Member;
import com.sadna.group13a.domain.Interfaces.IAdminRepository;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemServiceTest {

    @Mock private IUserRepository userRepository;
    @Mock private IAdminRepository adminRepository;
    @Mock private IAuth authGateway;
    @Mock private IPaymentGateway paymentGateway;
    @Mock private ITicketSupplier ticketingGateway;
    @Mock private IPasswordEncoder passwordEncoder;

    @InjectMocks
    private SystemService systemService;

    // ── initializePlatform ────────────────────────────────────────

    @Test
    void givenAllServicesUp_whenInitializePlatform_thenRootAdminCreated() {
        when(paymentGateway.isConnected()).thenReturn(true);
        when(ticketingGateway.isConnected()).thenReturn(true);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
        when(passwordEncoder.encodePassword("secret")).thenReturn("hashed");

        Result<Void> result = systemService.initializePlatform("admin", "secret");

        assertTrue(result.isSuccess());
        verify(userRepository).save(any(Member.class));
        verify(adminRepository).save(any(Admin.class));
    }

    @Test
    void givenPaymentGatewayDown_whenInitializePlatform_thenReturnsFailure() {
        when(paymentGateway.isConnected()).thenReturn(false);

        Result<Void> result = systemService.initializePlatform("admin", "secret");

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("payment"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void givenTicketingGatewayDown_whenInitializePlatform_thenReturnsFailure() {
        when(paymentGateway.isConnected()).thenReturn(true);
        when(ticketingGateway.isConnected()).thenReturn(false);

        Result<Void> result = systemService.initializePlatform("admin", "secret");

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("ticketing"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void givenAdminUsernameAlreadyTaken_whenInitializePlatform_thenReturnsFailure() {
        when(paymentGateway.isConnected()).thenReturn(true);
        when(ticketingGateway.isConnected()).thenReturn(true);
        when(userRepository.findByUsername("admin"))
                .thenReturn(Optional.of(new Member("a-1", "admin", "hash")));

        Result<Void> result = systemService.initializePlatform("admin", "secret");

        assertFalse(result.isSuccess());
        verify(userRepository, never()).save(any());
    }

    @Test
    void givenAlreadyInitialized_whenInitializePlatformCalledAgain_thenReturnsFailure() {
        when(paymentGateway.isConnected()).thenReturn(true);
        when(ticketingGateway.isConnected()).thenReturn(true);
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encodePassword(anyString())).thenReturn("hashed");

        systemService.initializePlatform("admin", "secret");  // first call succeeds
        Result<Void> secondCall = systemService.initializePlatform("admin", "secret");

        assertFalse(secondCall.isSuccess());
        assertTrue(secondCall.getErrorMessage().contains("already initialized"));
    }

    // ── isPlatformInitialized ─────────────────────────────────────

    @Test
    void givenFreshService_whenIsPlatformInitialized_thenReturnsFalse() {
        assertFalse(systemService.isPlatformInitialized());
    }

    @Test
    void givenSuccessfulInit_whenIsPlatformInitialized_thenReturnsTrue() {
        when(paymentGateway.isConnected()).thenReturn(true);
        when(ticketingGateway.isConnected()).thenReturn(true);
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encodePassword(anyString())).thenReturn("hashed");

        systemService.initializePlatform("admin", "secret");

        assertTrue(systemService.isPlatformInitialized());
    }
}
