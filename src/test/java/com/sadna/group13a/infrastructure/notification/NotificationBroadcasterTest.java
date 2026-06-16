package com.sadna.group13a.infrastructure.notification;

import com.sadna.group13a.domain.Interfaces.IPendingNotificationRepository;
import com.sadna.group13a.infrastructure.PendingNotification;
import com.vaadin.flow.component.UI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("NotificationBroadcaster")
class NotificationBroadcasterTest {

    private IPendingNotificationRepository pendingRepo;
    private NotificationBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        pendingRepo = mock(IPendingNotificationRepository.class);
        broadcaster = new NotificationBroadcaster(pendingRepo);
    }

    @Test
    @DisplayName("send to unregistered user saves message to pending repo")
    void send_offlineUser_savesToPendingRepo() {
        broadcaster.send("u1", "hello");
        verify(pendingRepo).save(any(PendingNotification.class));
    }

    @Test
    @DisplayName("register with no pending notifications — pending store is not cleared")
    void register_noPendingNotifications_doesNotDelete() {
        UI mockUi = mock(UI.class);
        when(pendingRepo.findByUserId("u1")).thenReturn(List.of());

        broadcaster.register("u1", mockUi);

        verify(pendingRepo, never()).deleteByUserId(any());
    }

    @Test
    @DisplayName("register with pending notifications — pending store is cleared and UI is accessed")
    void register_withPendingNotifications_deliversAndClears() {
        UI mockUi = mock(UI.class);
        PendingNotification pending = PendingNotification.of("u1", "deferred message");
        when(pendingRepo.findByUserId("u1")).thenReturn(List.of(pending));

        broadcaster.register("u1", mockUi);

        verify(pendingRepo).deleteByUserId("u1");
        verify(mockUi, timeout(500)).access(any());
    }

    @Test
    @DisplayName("send to registered user — calls ui.access to push the message")
    void send_onlineUser_callsUiAccess() {
        UI mockUi = mock(UI.class);
        when(pendingRepo.findByUserId("u1")).thenReturn(List.of());
        broadcaster.register("u1", mockUi);

        broadcaster.send("u1", "live message");

        verify(mockUi, timeout(500)).access(any());
    }

    @Test
    @DisplayName("unregister — subsequent send goes to offline path and saves to pending repo")
    void unregister_subsequentSendIsOffline() {
        UI mockUi = mock(UI.class);
        when(pendingRepo.findByUserId("u1")).thenReturn(List.of());
        broadcaster.register("u1", mockUi);
        broadcaster.unregister("u1");

        broadcaster.send("u1", "after logout");

        verify(pendingRepo).save(any(PendingNotification.class));
    }
}
