package com.sadna.group13a.application.Services;

import com.sadna.group13a.domain.Interfaces.IUserNotificationRepository;
import com.sadna.group13a.infrastructure.UserNotification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("UserNotificationService")
class UserNotificationServiceTest {

    private IUserNotificationRepository repository;
    private UserNotificationService service;

    @BeforeEach
    void setUp() {
        repository = mock(IUserNotificationRepository.class);
        service = new UserNotificationService(repository);
    }

    @Test
    @DisplayName("saveGeneral persists a GENERAL notification for the user")
    void saveGeneral_persistsGeneral() {
        service.saveGeneral("u1", "hello");

        ArgumentCaptor<UserNotification> captor = ArgumentCaptor.forClass(UserNotification.class);
        verify(repository).save(captor.capture());
        UserNotification saved = captor.getValue();
        assertEquals("u1", saved.userId());
        assertEquals("hello", saved.message());
        assertEquals(UserNotification.TYPE_GENERAL, saved.type());
    }

    @Test
    @DisplayName("saveNomination persists a STAFF_NOMINATION notification carrying the companyId")
    void saveNomination_persistsNominationWithCompany() {
        service.saveNomination("u1", "You were invited", "c1");

        ArgumentCaptor<UserNotification> captor = ArgumentCaptor.forClass(UserNotification.class);
        verify(repository).save(captor.capture());
        UserNotification saved = captor.getValue();
        assertEquals(UserNotification.TYPE_STAFF_NOMINATION, saved.type());
        assertEquals("c1", saved.metadata());
    }

    @Test
    @DisplayName("getForUser delegates to the repository")
    void getForUser_delegates() {
        UserNotification n = UserNotification.general("u1", "hi");
        when(repository.findByUserIdOrderByCreatedAtDesc("u1")).thenReturn(List.of(n));

        assertEquals(List.of(n), service.getForUser("u1"));
        verify(repository).findByUserIdOrderByCreatedAtDesc("u1");
    }

    @Test
    @DisplayName("dismiss deletes the notification by id")
    void dismiss_deletesById() {
        service.dismiss("notif-1");
        verify(repository).deleteById("notif-1");
    }

    @Test
    @DisplayName("Issue #368 — dismissNomination clears the user's nomination for the company")
    void dismissNomination_delegatesToRepository() {
        service.dismissNomination("u1", "c1");
        verify(repository).deleteNominations("u1", "c1");
    }
}
