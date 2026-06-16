package com.sadna.group13a.infrastructure.notification;

import com.sadna.group13a.domain.Interfaces.IPendingNotificationRepository;
import com.sadna.group13a.infrastructure.PendingNotification;
import com.vaadin.flow.component.UI;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class NotificationBroadcaster {

    private final Map<String, UI> sessions = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final IPendingNotificationRepository pendingRepo;

    public NotificationBroadcaster(IPendingNotificationRepository pendingRepo) {
        this.pendingRepo = pendingRepo;
    }

    /**
     * Called when a user's Vaadin UI session attaches (typically after login).
     * Delivers any notifications that arrived while the user was offline.
     */
    public void register(String userId, UI ui) {
        sessions.put(userId, ui);
        List<PendingNotification> pending = pendingRepo.findByUserId(userId);
        if (!pending.isEmpty()) {
            pendingRepo.deleteByUserId(userId);
            pending.forEach(n -> pushToUi(ui, n.message()));
        }
    }

    public void unregister(String userId) {
        sessions.remove(userId);
    }

    /**
     * Sends a notification to the user.
     * If the user is online the message is pushed immediately via WebSocket.
     * If the user is offline the message is stored and delivered on next login.
     */
    public void send(String userId, String message) {
        UI ui = sessions.get(userId);
        if (ui != null) {
            pushToUi(ui, message);
        } else {
            pendingRepo.save(PendingNotification.of(userId, message));
        }
    }

    private void pushToUi(UI ui, String message) {
        executor.submit(() -> ui.access(() -> ui.getPage().executeJs(
            "window.dispatchEvent(new CustomEvent('notification', { detail: $0 }))", message
        )));
    }
}
