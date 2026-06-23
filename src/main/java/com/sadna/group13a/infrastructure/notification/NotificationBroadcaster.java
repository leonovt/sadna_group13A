package com.sadna.group13a.infrastructure.notification;

import com.sadna.group13a.application.Services.UserNotificationService;
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
    private final UserNotificationService userNotificationService;

    public NotificationBroadcaster(IPendingNotificationRepository pendingRepo,
                                    UserNotificationService userNotificationService) {
        this.pendingRepo = pendingRepo;
        this.userNotificationService = userNotificationService;
    }

    /**
     * Registers a user's live Vaadin UI session (typically on attach / after login). This is an
     * in-memory concern only — it performs <b>no</b> database access, because it runs on the
     * Vaadin UI thread with no active transaction. Delivery of any notifications that arrived
     * while the user was offline is driven by the presenter via
     * {@link com.sadna.group13a.application.Services.PendingNotificationService#drainPending(String)}
     * (a transactional read-and-clear) followed by {@link #deliverPending(UI, List)}.
     */
    public void register(String userId, UI ui) {
        sessions.put(userId, ui);
    }

    /** Pushes already-drained pending messages to the freshly attached UI (no DB access). */
    public void deliverPending(UI ui, List<String> messages) {
        if (messages == null) {
            return;
        }
        messages.forEach(message -> pushToUi(ui, message));
    }

    public void unregister(String userId) {
        sessions.remove(userId);
    }

    /**
     * Sends a notification to the user.
     * Always persists to the user's notification inbox, then pushes immediately
     * via WebSocket if online, or queues for deferred delivery if offline.
     */
    public void send(String userId, String message) {
        userNotificationService.saveGeneral(userId, message);
        dispatch(userId, message);
    }

    /**
     * Sends a staff nomination notification, storing the companyId so the
     * notifications page can render Accept / Decline action buttons.
     */
    public void sendNomination(String userId, String message, String companyId) {
        userNotificationService.saveNomination(userId, message, companyId);
        dispatch(userId, message);
    }

    private void dispatch(String userId, String message) {
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
