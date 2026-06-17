package com.sadna.group13a.application.Services;

import com.sadna.group13a.domain.Aggregates.User.User;
import com.sadna.group13a.domain.Events.UserReactivatedEvent;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;

/**
 * Unit tests for SuspensionExpiryJob.liftExpiredSuspensions().
 *
 * All tests use Mockito to isolate the job from infrastructure.
 */
class SuspensionExpiryJobTest {

    private IUserRepository userRepository;
    private ApplicationEventPublisher eventPublisher;
    private SystemLogService systemLogService;
    private SuspensionExpiryJob job;

    @BeforeEach
    void setUp() {
        userRepository = mock(IUserRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        systemLogService = mock(SystemLogService.class);
        job = new SuspensionExpiryJob(userRepository, eventPublisher, systemLogService);
    }

    @Test
    void givenNoUsers_whenJobRuns_thenNothingHappens() {
        when(userRepository.findAll()).thenReturn(List.of());

        job.liftExpiredSuspensions();

        verify(userRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
        verify(systemLogService, never()).logEvent(any());
    }

    @Test
    void givenActiveUser_whenJobRuns_thenUserNotTouched() {
        User active = mock(User.class);
        when(active.isSuspended()).thenReturn(false);
        when(userRepository.findAll()).thenReturn(List.of(active));

        job.liftExpiredSuspensions();

        verify(active, never()).activate();
        verify(userRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
        verify(systemLogService, never()).logEvent(any());
    }

    @Test
    void givenSuspendedUserWithFutureExpiry_whenJobRuns_thenNotLifted() {
        User suspended = mock(User.class);
        when(suspended.isSuspended()).thenReturn(true);
        when(suspended.getSuspendedUntil()).thenReturn(LocalDateTime.now().plusHours(1));
        when(userRepository.findAll()).thenReturn(List.of(suspended));

        job.liftExpiredSuspensions();

        verify(suspended, never()).activate();
        verify(userRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
        verify(systemLogService, never()).logEvent(any());
    }

    @Test
    void givenSuspendedUserWithPastExpiry_whenJobRuns_thenLifted() {
        User expired = mock(User.class);
        when(expired.isSuspended()).thenReturn(true);
        when(expired.getSuspendedUntil()).thenReturn(LocalDateTime.now().minusHours(1));
        when(expired.getId()).thenReturn("user-1");
        when(expired.getUsername()).thenReturn("alice");
        when(userRepository.findAll()).thenReturn(List.of(expired));

        job.liftExpiredSuspensions();

        verify(expired).activate();
        verify(userRepository).save(expired);
        verify(eventPublisher).publishEvent(new UserReactivatedEvent("user-1", "system"));
        verify(systemLogService).logEvent(anyString());
    }

    @Test
    void givenMixOfPastAndFutureExpiry_whenJobRuns_thenOnlyPastExpiryLifted() {
        User expired = mock(User.class);
        when(expired.isSuspended()).thenReturn(true);
        when(expired.getSuspendedUntil()).thenReturn(LocalDateTime.now().minusHours(1));
        when(expired.getId()).thenReturn("user-1");
        when(expired.getUsername()).thenReturn("alice");

        User future = mock(User.class);
        when(future.isSuspended()).thenReturn(true);
        when(future.getSuspendedUntil()).thenReturn(LocalDateTime.now().plusHours(1));

        when(userRepository.findAll()).thenReturn(List.of(expired, future));

        job.liftExpiredSuspensions();

        verify(expired).activate();
        verify(userRepository).save(expired);
        verify(future, never()).activate();
        verify(userRepository, times(1)).save(any());
    }

    @Test
    void givenSaveThrows_whenJobRuns_thenExceptionNotPropagated() {
        User expired = mock(User.class);
        when(expired.isSuspended()).thenReturn(true);
        when(expired.getSuspendedUntil()).thenReturn(LocalDateTime.now().minusHours(1));
        when(expired.getId()).thenReturn("user-1");
        when(expired.getUsername()).thenReturn("alice");
        when(userRepository.findAll()).thenReturn(List.of(expired));

        doThrow(new RuntimeException("db down")).when(userRepository).save(expired);

        job.liftExpiredSuspensions();

        verify(eventPublisher, never()).publishEvent(any());
    }
}
