package com.sadna.group13a.infrastructure.notification;

import com.sadna.group13a.application.Services.UserNotificationService;
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
    private UserNotificationService userNotificationService;
    private NotificationBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        pendingRepo = mock(IPendingNotificationRepository.class);
        userNotificationService = mock(UserNotificationService.class);
        broadcaster = new NotificationBroadcaster(pendingRepo, userNotificationService);
    }

    @Test
    @DisplayName("send to unregistered user saves message to pending repo")
    void send_offlineUser_savesToPendingRepo() {
        broadcaster.send("u1", "hello");
        verify(pendingRepo).save(any(PendingNotification.class));
    }

    @Test
    @DisplayName("register only stores the session — it performs NO database access (no transaction on the UI thread)")
    void register_doesNotTouchPendingRepo() {
        UI mockUi = mock(UI.class);

        broadcaster.register("u1", mockUi);

        // The fix: draining pending notifications moved to PendingNotificationService
        // (transactional). register() must never read/delete from the repository.
        verifyNoInteractions(pendingRepo);
    }

    @Test
    @DisplayName("deliverPending pushes each already-drained message to the UI and never touches the DB")
    void deliverPending_pushesEachMessage() {
        UI mockUi = mock(UI.class);

        broadcaster.deliverPending(mockUi, List.of("deferred one", "deferred two"));

        verify(mockUi, timeout(500).times(2)).access(any());
        verifyNoInteractions(pendingRepo);
    }

    @Test
    @DisplayName("deliverPending with null/empty list is a no-op")
    void deliverPending_nullOrEmpty_noop() {
        UI mockUi = mock(UI.class);

        broadcaster.deliverPending(mockUi, null);
        broadcaster.deliverPending(mockUi, List.of());

        verify(mockUi, never()).access(any());
        verifyNoInteractions(pendingRepo);
    }

    @Test
    @DisplayName("send to registered user — calls ui.access to push the message")
    void send_onlineUser_callsUiAccess() {
        UI mockUi = mock(UI.class);
        broadcaster.register("u1", mockUi);

        broadcaster.send("u1", "live message");

        verify(mockUi, timeout(500)).access(any());
    }

    @Test
    @DisplayName("unregister — subsequent send goes to offline path and saves to pending repo")
    void unregister_subsequentSendIsOffline() {
        UI mockUi = mock(UI.class);
        broadcaster.register("u1", mockUi);
        broadcaster.unregister("u1");

        broadcaster.send("u1", "after logout");

        verify(pendingRepo).save(any(PendingNotification.class));
    }
}
